package com.toasty.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

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
                                "ap-northeast-2",
                                "toasty-media",
                                "products/pending/",
                                "products/images/",
                                "sellers/images/",
                                300,
                                "https://cdn.example.com"));

        given(productRepository.save(any(Product.class))).willAnswer(c -> c.getArgument(0));
        given(productImageRepository.save(any(ProductImage.class)))
                .willAnswer(c -> c.getArgument(0));
        given(liveProductRepository.save(any(LiveProduct.class))).willAnswer(c -> c.getArgument(0));
    }

    private void givenCopySucceeds() {
        given(s3Client.copyObject(any(CopyObjectRequest.class)))
                .willReturn(CopyObjectResponse.builder().build());
    }

    private static ProductCreateCommand command(String name, String key) {
        return new ProductCreateCommand(name, 45000, 1, "소가죽 100%", key);
    }

    @Test
    @DisplayName("상품·사진·편성을 함께 만들고 보낸 순서를 노출 순서로 쓴다")
    void 순서대로_등록한다() {
        List<LiveProductResponse> responses =
                productService.registerForLive(
                        LIVE_ID,
                        SELLER_ID,
                        List.of(
                                command("가죽 벨트", "products/pending/7/a.jpg"),
                                command("도자기 컵", "products/pending/7/b.jpg")),
                        List.of("products/images/7/a.jpg", "products/images/7/b.jpg"));

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
    @DisplayName("대표 이미지 주소는 공개 주소와 옮겨진 객체 키를 이어 붙인다")
    void 이미지_주소를_만든다() {
        List<LiveProductResponse> responses =
                productService.registerForLive(
                        LIVE_ID,
                        SELLER_ID,
                        List.of(command("가죽 벨트", "products/pending/7/a.jpg")),
                        List.of("products/images/7/a.jpg"));

        assertThat(responses.get(0).imageUrl())
                .isEqualTo("https://cdn.example.com/products/images/7/a.jpg");
    }

    @Test
    @DisplayName("사진 키가 없으면 PRODUCT_IMAGE_REQUIRED다")
    void 사진이_없으면_거부한다() {
        assertThatThrownBy(
                        () ->
                                productService.copyImagesToPermanent(
                                        SELLER_ID, List.of(command("가죽 벨트", "  "))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.PRODUCT_IMAGE_REQUIRED);
    }

    @Test
    @DisplayName("업로드가 끝나지 않은 사진이면 PRODUCT_IMAGE_NOT_UPLOADED다")
    void 업로드_안된_사진이면_거부한다() {
        willThrow(NoSuchKeyException.builder().message("없음").build())
                .given(s3Client)
                .copyObject(any(CopyObjectRequest.class));

        assertThatThrownBy(
                        () ->
                                productService.copyImagesToPermanent(
                                        SELLER_ID,
                                        List.of(command("가죽 벨트", "products/pending/7/a.jpg"))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.PRODUCT_IMAGE_NOT_UPLOADED);
    }

    @Test
    @DisplayName("S3가 응답하지 않으면 클라이언트 잘못이 아니므로 PRODUCT_IMAGE_SAVE_FAILED다")
    void S3_장애면_502다() {
        willThrow(S3Exception.builder().statusCode(503).message("서비스 이용 불가").build())
                .given(s3Client)
                .copyObject(any(CopyObjectRequest.class));

        assertThatThrownBy(
                        () ->
                                productService.copyImagesToPermanent(
                                        SELLER_ID,
                                        List.of(command("가죽 벨트", "products/pending/7/a.jpg"))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.PRODUCT_IMAGE_SAVE_FAILED);
    }

    @Test
    @DisplayName("버킷 설정이 틀린 것도 서버 문제라 PRODUCT_IMAGE_SAVE_FAILED다")
    void 버킷이_없으면_502다() {
        willThrow(NoSuchBucketException.builder().message("버킷 없음").build())
                .given(s3Client)
                .copyObject(any(CopyObjectRequest.class));

        assertThatThrownBy(
                        () ->
                                productService.copyImagesToPermanent(
                                        SELLER_ID,
                                        List.of(command("가죽 벨트", "products/pending/7/a.jpg"))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.PRODUCT_IMAGE_SAVE_FAILED);
    }

    @Test
    @DisplayName("사진 검증은 상품마다 한 번씩만 S3에 묻는다")
    void 상품마다_한_번씩_확인한다() {
        givenCopySucceeds();

        productService.copyImagesToPermanent(
                SELLER_ID,
                List.of(
                        command("가죽 벨트", "products/pending/7/a.jpg"),
                        command("도자기 컵", "products/pending/7/b.jpg")));

        verify(s3Client, times(2)).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    @DisplayName("트랜잭션이 걸리는 저장 구간에서는 S3를 호출하지 않는다")
    void 저장_구간에서는_S3를_부르지_않는다() {
        productService.registerForLive(
                LIVE_ID,
                SELLER_ID,
                List.of(command("가죽 벨트", "products/pending/7/a.jpg")),
                List.of("products/images/7/a.jpg"));

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    @DisplayName("영구 경로는 접두어만 바뀌고 셀러·날짜·uuid는 그대로다")
    void 접두어만_바꿔_복사한다() {
        givenCopySucceeds();

        List<String> keys =
                productService.copyImagesToPermanent(
                        SELLER_ID,
                        List.of(command("가죽 벨트", "products/pending/7/2026/09/04/abc-def.jpg")));

        assertThat(keys).containsExactly("products/images/7/2026/09/04/abc-def.jpg");

        ArgumentCaptor<CopyObjectRequest> captor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(captor.capture());
        assertThat(captor.getValue().sourceKey())
                .isEqualTo("products/pending/7/2026/09/04/abc-def.jpg");
        assertThat(captor.getValue().destinationKey())
                .isEqualTo("products/images/7/2026/09/04/abc-def.jpg");
    }

    @Test
    @DisplayName("뒤쪽 사진 복사가 실패하면 앞서 복사한 사진을 되돌린다")
    void 중간에_실패하면_복사본을_지운다() {
        given(s3Client.copyObject(any(CopyObjectRequest.class)))
                .willReturn(CopyObjectResponse.builder().build())
                .willThrow(NoSuchKeyException.builder().message("없음").build());

        assertThatThrownBy(
                        () ->
                                productService.copyImagesToPermanent(
                                        SELLER_ID,
                                        List.of(
                                                command("가죽 벨트", "products/pending/7/a.jpg"),
                                                command("도자기 컵", "products/pending/7/b.jpg"))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.PRODUCT_IMAGE_NOT_UPLOADED);

        ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("products/images/7/a.jpg");
    }
}
