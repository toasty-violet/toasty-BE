package com.toasty.domain.product.service;

import com.toasty.domain.product.controller.dto.response.LiveProductResponse;
import com.toasty.domain.product.entity.LiveProduct;
import com.toasty.domain.product.entity.Product;
import com.toasty.domain.product.entity.ProductCreateCommand;
import com.toasty.domain.product.entity.ProductImage;
import com.toasty.domain.product.entity.ProductUpsertCommand;
import com.toasty.domain.product.exception.ProductErrorCode;
import com.toasty.domain.product.repository.LiveProductRepository;
import com.toasty.domain.product.repository.ProductImageRepository;
import com.toasty.domain.product.repository.ProductRepository;
import com.toasty.global.config.S3Properties;
import com.toasty.global.exception.CustomException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** 셀러가 라이브에서 판매할 상품을 등록하고 그 라이브에 편성한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final LiveProductRepository liveProductRepository;
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    /** 라이브를 저장하기 전에 사진을 영구 경로로 복사하고 그 키를 보낸 순서대로 돌려준다. */
    // 트랜잭션 밖에서 돌아야 한다. S3 왕복이 상품 수만큼 반복돼 DB 커넥션을 잡고 있으면 안 된다.
    // 원본은 지우지 않는다. pending 경로는 수명주기 규칙이 정리하므로, 지우다 실패해 사진이 사라지는 경우를 아예 만들지 않는다.
    public List<String> copyImagesToPermanent(Long sellerId, List<ProductCreateCommand> commands) {
        List<String> copied = new ArrayList<>();
        try {
            for (ProductCreateCommand command : commands) {
                copied.add(copyToPermanent(sellerId, command.imageObjectKey()));
            }
            return copied;
        } catch (RuntimeException e) {
            deleteImagesQuietly(copied);
            throw e;
        }
    }

    /** 라이브 저장이 실패했을 때 이미 복사해 둔 사진을 치운다. */
    // 지우기 실패가 원래 예외를 가리지 않게 한다. 남은 객체는 로그로 추적한다.
    public void deleteImagesQuietly(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            if (objectKey == null) {
                continue;
            }
            try {
                s3Client.deleteObject(
                        DeleteObjectRequest.builder()
                                .bucket(s3Properties.bucket())
                                .key(objectKey)
                                .build());
            } catch (SdkException e) {
                log.error("상품 사진 정리 실패. 버킷에 고아 객체가 남았다 - objectKey={}", objectKey, e);
            }
        }
    }

    /**
     * 라이브를 저장할 때 함께 넘어온 상품들을 만들고 그 라이브에 편성한다. S3를 건드리지 않으므로 사진은 호출 전에 {@link
     * #copyImagesToPermanent}로 옮겨 두고 그 키를 같은 순서로 넘겨야 한다.
     */
    @Transactional
    public List<LiveProductResponse> registerForLive(
            Long liveId,
            Long sellerId,
            List<ProductCreateCommand> commands,
            List<String> imageObjectKeys) {
        List<LiveProductResponse> responses = new ArrayList<>();
        for (int order = 0; order < commands.size(); order++) {
            responses.add(
                    register(
                            liveId,
                            sellerId,
                            commands.get(order),
                            imageObjectKeys.get(order),
                            order));
        }
        return responses;
    }

    /** 라이브를 수정하기 전에 이번에 새로 올라온 사진만 영구 경로로 복사한다. 사진을 바꾸지 않은 자리는 null로 둔다. */
    // copyImagesToPermanent와 같은 이유로 트랜잭션 밖에서 돌아야 한다.
    public List<String> copyNewImagesToPermanent(
            Long sellerId, List<ProductUpsertCommand> commands) {
        List<String> copied = new ArrayList<>();
        try {
            for (ProductUpsertCommand command : commands) {
                if (command.isNew() && command.imageObjectKey() == null) {
                    throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_REQUIRED);
                }
                copied.add(
                        command.imageObjectKey() == null
                                ? null
                                : copyToPermanent(sellerId, command.imageObjectKey()));
            }
            return copied;
        } catch (RuntimeException e) {
            deleteImagesQuietly(copied);
            throw e;
        }
    }

    /**
     * 라이브 수정 화면에서 넘어온 상품 배열로 편성을 통째로 바꾸고, 더 이상 쓰지 않는 사진의 objectKey를 돌려준다. S3를 건드리지 않으므로 사진은 호출 전에
     * {@link #copyNewImagesToPermanent}로 옮겨 두고 그 키를 같은 순서로 넘겨야 한다. 돌려받은 키는 커밋된 뒤에 지운다.
     */
    @Transactional
    public List<String> replaceForLive(
            Long liveId,
            Long sellerId,
            List<ProductUpsertCommand> commands,
            List<String> newImageObjectKeys) {
        Map<Long, LiveProduct> scheduled =
                liveProductRepository.findByLiveId(liveId).stream()
                        .collect(Collectors.toMap(LiveProduct::getProductId, Function.identity()));

        List<String> obsoleteImageObjectKeys = new ArrayList<>();
        Set<Long> keptProductIds = new HashSet<>();
        for (int order = 0; order < commands.size(); order++) {
            ProductUpsertCommand command = commands.get(order);
            String imageObjectKey = newImageObjectKeys.get(order);
            if (command.isNew()) {
                register(liveId, sellerId, toCreateCommand(command), imageObjectKey, order);
                continue;
            }
            obsoleteImageObjectKeys.addAll(
                    modify(
                            scheduled.get(command.productId()),
                            sellerId,
                            command,
                            imageObjectKey,
                            order));
            keptProductIds.add(command.productId());
        }

        for (LiveProduct liveProduct : scheduled.values()) {
            if (!keptProductIds.contains(liveProduct.getProductId())) {
                obsoleteImageObjectKeys.addAll(unschedule(liveProduct));
            }
        }
        return obsoleteImageObjectKeys;
    }

    // 편성 여부와 셀러를 함께 본다. 남의 라이브 상품이나 편성되지 않은 상품 번호로는 통과할 수 없다.
    private List<String> modify(
            LiveProduct liveProduct,
            Long sellerId,
            ProductUpsertCommand command,
            String imageObjectKey,
            int displayOrder) {
        if (liveProduct == null) {
            throw new CustomException(ProductErrorCode.PRODUCT_NOT_IN_LIVE);
        }
        Product product =
                productRepository
                        .findById(command.productId())
                        .orElseThrow(
                                () -> new CustomException(ProductErrorCode.PRODUCT_NOT_IN_LIVE));
        if (!product.getSellerId().equals(sellerId)) {
            throw new CustomException(ProductErrorCode.PRODUCT_NOT_IN_LIVE);
        }

        product.update(
                command.name(), command.price(), command.stockQuantity(), command.description());
        liveProduct.changeDisplayOrder(displayOrder);
        if (imageObjectKey == null) {
            return List.of();
        }
        return replaceMainImage(product.getId(), imageObjectKey);
    }

    private List<String> replaceMainImage(Long productId, String imageObjectKey) {
        List<ProductImage> images =
                productImageRepository.findByProductIdOrderByDisplayOrder(productId);
        if (images.isEmpty()) {
            productImageRepository.save(
                    ProductImage.createMain(productId, toImageUrl(imageObjectKey)));
            return List.of();
        }
        ProductImage main = images.get(0);
        String replaced = main.getImageUrl();
        main.changeImageUrl(toImageUrl(imageObjectKey));
        return toObjectKey(replaced).map(List::of).orElseGet(List::of);
    }

    // 다른 라이브에도 편성돼 있으면 편성만 푼다. 그 라이브에서 상품이 사라지면 안 된다.
    private List<String> unschedule(LiveProduct liveProduct) {
        liveProductRepository.delete(liveProduct);
        Long productId = liveProduct.getProductId();
        if (liveProductRepository.existsByProductIdAndLiveIdNot(
                productId, liveProduct.getLiveId())) {
            return List.of();
        }

        List<ProductImage> images =
                productImageRepository.findByProductIdOrderByDisplayOrder(productId);
        List<String> objectKeys =
                images.stream()
                        .map(image -> toObjectKey(image.getImageUrl()))
                        .flatMap(Optional::stream)
                        .toList();
        productImageRepository.deleteAll(images);
        productRepository.deleteById(productId);
        return objectKeys;
    }

    private ProductCreateCommand toCreateCommand(ProductUpsertCommand command) {
        return new ProductCreateCommand(
                command.name(),
                command.price(),
                command.stockQuantity(),
                command.description(),
                command.imageObjectKey());
    }

    private LiveProductResponse register(
            Long liveId,
            Long sellerId,
            ProductCreateCommand command,
            String imageObjectKey,
            int displayOrder) {
        Product product = productRepository.save(Product.createForLive(sellerId, command));
        ProductImage image =
                productImageRepository.save(
                        ProductImage.createMain(product.getId(), toImageUrl(imageObjectKey)));
        LiveProduct liveProduct =
                liveProductRepository.save(
                        LiveProduct.schedule(liveId, product.getId(), displayOrder));

        return LiveProductResponse.of(product, liveProduct, image.getImageUrl());
    }

    // 복사가 곧 검증이다. 업로드 주소만 받고 실제로 올리지 않았으면 원본이 없어 NoSuchKey가 난다.
    private String copyToPermanent(Long sellerId, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_REQUIRED);
        }
        requireOwnedBySeller(sellerId, objectKey);

        String destinationKey = toPermanentKey(objectKey);
        try {
            s3Client.copyObject(
                    CopyObjectRequest.builder()
                            .sourceBucket(s3Properties.bucket())
                            .sourceKey(objectKey)
                            .destinationBucket(s3Properties.bucket())
                            .destinationKey(destinationKey)
                            .build());
            return destinationKey;
        } catch (NoSuchKeyException e) {
            throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_NOT_UPLOADED, e);
        } catch (SdkException e) {
            log.error("상품 사진 복사 실패. objectKey={}", objectKey, e);
            throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_SAVE_FAILED, e);
        }
    }

    // 셀러·날짜·uuid를 그대로 두고 접두어만 바꾼다. 이미 pending 접두어로 시작하는지 검증한 뒤라 안전하다.
    private String toPermanentKey(String objectKey) {
        return s3Properties.imagePrefix()
                + objectKey.substring(s3Properties.pendingPrefix().length());
    }

    // 업로드 주소를 발급할 때 키에 넣은 셀러 번호로, 남의 사진을 자기 상품에 붙이는 것을 막는다.
    private void requireOwnedBySeller(Long sellerId, String objectKey) {
        if (!objectKey.startsWith(s3Properties.pendingPrefix() + sellerId + "/")) {
            throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_FORBIDDEN);
        }
    }

    private String toImageUrl(String objectKey) {
        return s3Properties.publicBaseUrl() + "/" + objectKey;
    }

    // publicBaseUrl이 바뀐 뒤에 저장된 주소는 지울 키를 알 수 없어 그대로 남긴다. 사진이 사라지는 방향의 실패를 만들지 않는다.
    private Optional<String> toObjectKey(String imageUrl) {
        String prefix = s3Properties.publicBaseUrl() + "/";
        if (!imageUrl.startsWith(prefix)) {
            log.warn("사진 주소에서 objectKey를 얻지 못해 정리를 건너뛴다 - imageUrl={}", imageUrl);
            return Optional.empty();
        }
        return Optional.of(imageUrl.substring(prefix.length()));
    }
}
