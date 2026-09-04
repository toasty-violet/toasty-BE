package com.toasty.domain.product.service;

import com.toasty.domain.product.controller.dto.response.LiveProductResponse;
import com.toasty.domain.product.entity.LiveProduct;
import com.toasty.domain.product.entity.Product;
import com.toasty.domain.product.entity.ProductCreateCommand;
import com.toasty.domain.product.entity.ProductImage;
import com.toasty.domain.product.exception.ProductErrorCode;
import com.toasty.domain.product.repository.LiveProductRepository;
import com.toasty.domain.product.repository.ProductImageRepository;
import com.toasty.domain.product.repository.ProductRepository;
import com.toasty.global.config.S3Properties;
import com.toasty.global.exception.CustomException;
import java.util.ArrayList;
import java.util.List;
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
}
