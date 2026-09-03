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
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
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

    /** 라이브를 저장하기 전에 사진이 실제로 올라왔는지 미리 확인한다. */
    // 트랜잭션 밖에서 돌아야 한다. S3 왕복이 상품 수만큼 반복돼 DB 커넥션을 잡고 있으면 안 된다.
    public void validateImages(Long sellerId, List<ProductCreateCommand> commands) {
        commands.forEach(command -> requireUploadedImage(sellerId, command.imageObjectKey()));
    }

    /**
     * 라이브를 저장할 때 함께 넘어온 상품들을 만들고 그 라이브에 편성한다. 사진 검증은 하지 않으므로 호출 전에 {@link #validateImages}로 끝나 있어야
     * 한다.
     */
    @Transactional
    public List<LiveProductResponse> registerForLive(
            Long liveId, Long sellerId, List<ProductCreateCommand> commands) {
        List<LiveProductResponse> responses = new ArrayList<>();
        for (int order = 0; order < commands.size(); order++) {
            responses.add(register(liveId, sellerId, commands.get(order), order));
        }
        return responses;
    }

    private LiveProductResponse register(
            Long liveId, Long sellerId, ProductCreateCommand command, int displayOrder) {
        Product product = productRepository.save(Product.createForLive(sellerId, command));
        ProductImage image =
                productImageRepository.save(
                        ProductImage.createMain(
                                product.getId(), toImageUrl(command.imageObjectKey())));
        LiveProduct liveProduct =
                liveProductRepository.save(
                        LiveProduct.schedule(liveId, product.getId(), displayOrder));

        return LiveProductResponse.of(product, liveProduct, image.getImageUrl());
    }

    // 업로드 주소만 받고 실제로 올리지 않은 채 저장을 누른 경우를 걸러낸다.
    private void requireUploadedImage(Long sellerId, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_REQUIRED);
        }
        requireOwnedBySeller(sellerId, objectKey);
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(s3Properties.bucket())
                            .key(objectKey)
                            .build());
        } catch (NoSuchKeyException e) {
            throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_NOT_UPLOADED, e);
        } catch (SdkException e) {
            log.error("상품 사진 확인 실패. objectKey={}", objectKey, e);
            throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_CHECK_FAILED, e);
        }
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
