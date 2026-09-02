package com.toasty.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.toasty.domain.product.controller.dto.response.LiveProductResponse;
import com.toasty.domain.product.entity.LiveProduct;
import com.toasty.domain.product.entity.LiveProductStatus;
import com.toasty.domain.product.entity.Product;
import com.toasty.domain.product.entity.ProductCreateCommand;
import com.toasty.domain.product.entity.ProductImage;
import com.toasty.domain.product.exception.ProductErrorCode;
import com.toasty.domain.product.repository.LiveProductRepository;
import com.toasty.domain.product.repository.ProductImageRepository;
import com.toasty.domain.product.repository.ProductRepository;
import com.toasty.global.config.S3Properties;
import com.toasty.global.exception.CustomException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@DisplayName("라이브 상품 등록")
class ProductServiceTest {

    private static final Long LIVE_ID = 12L;
    private static final Long SELLER_ID = 7L;

    private ProductRepository productRepository;
    private ProductImageRepository productImageRepository;
    private LiveProductRepository liveProductRepository;
    private S3Client s3Client;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productImageRepository = mock(ProductImageRepository.class);
        liveProductRepository = mock(LiveProductRepository.class);
        s3Client = mock(S3Client.class);
        productService =
                new ProductService(
                        productRepository,
                        productImageRepository,
                        liveProductRepository,
                        s3Client,
                        new S3Properties(
                                "toasty-media",
                                "products/pending/",
                                300,
                                "https://cdn.example.com"));

        given(productRepository.save(any(Product.class))).willAnswer(c -> c.getArgument(0));
        given(productImageRepository.save(any(ProductImage.class)))
                .willAnswer(c -> c.getArgument(0));
        given(liveProductRepository.save(any(LiveProduct.class))).willAnswer(c -> c.getArgument(0));
    }

    private void givenImageUploaded() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().build());
    }

    private static ProductCreateCommand command(String name, String key) {
        return new ProductCreateCommand(name, 45000, 1, "소가죽 100%", key);
    }

    @Test
    @DisplayName("상품·사진·편성을 함께 만들고 보낸 순서를 노출 순서로 쓴다")
    void 순서대로_등록한다() {
        givenImageUploaded();

        List<LiveProductResponse> responses =
                productService.registerForLive(
                        LIVE_ID,
                        SELLER_ID,
                        List.of(
                                command("가죽 벨트", "products/pending/7/a.jpg"),
                                command("도자기 컵", "products/pending/7/b.jpg")));

        assertThat(responses).hasSize(2);
        assertThat(responses)
                .extracting(LiveProductResponse::name)
                .containsExactly("가죽 벨트", "도자기 컵");
        assertThat(responses).extracting(LiveProductResponse::displayOrder).containsExactly(0, 1);
        assertThat(responses)
                .extracting(LiveProductResponse::status)
                .containsOnly(LiveProductStatus.SCHEDULED);
    }

    @Test
    @DisplayName("대표 이미지 주소는 공개 주소와 객체 키를 이어 붙인다")
    void 이미지_주소를_만든다() {
        givenImageUploaded();

        List<LiveProductResponse> responses =
                productService.registerForLive(
                        LIVE_ID, SELLER_ID, List.of(command("가죽 벨트", "products/pending/7/a.jpg")));

        assertThat(responses.get(0).imageUrl())
                .isEqualTo("https://cdn.example.com/products/pending/7/a.jpg");
    }

    @Test
    @DisplayName("사진 키가 없으면 PRODUCT_IMAGE_REQUIRED이고 아무것도 저장하지 않는다")
    void 사진이_없으면_거부한다() {
        assertThatThrownBy(
                        () ->
                                productService.registerForLive(
                                        LIVE_ID, SELLER_ID, List.of(command("가죽 벨트", "  "))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.PRODUCT_IMAGE_REQUIRED);

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("업로드가 끝나지 않은 사진이면 PRODUCT_IMAGE_NOT_UPLOADED다")
    void 업로드_안된_사진이면_거부한다() {
        willThrow(NoSuchKeyException.builder().message("없음").build())
                .given(s3Client)
                .headObject(any(HeadObjectRequest.class));

        assertThatThrownBy(
                        () ->
                                productService.registerForLive(
                                        LIVE_ID,
                                        SELLER_ID,
                                        List.of(command("가죽 벨트", "products/pending/7/a.jpg"))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.PRODUCT_IMAGE_NOT_UPLOADED);

        verify(productRepository, never()).save(any(Product.class));
    }
}
