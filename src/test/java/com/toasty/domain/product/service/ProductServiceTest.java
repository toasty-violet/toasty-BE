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
import com.toasty.domain.product.entity.ProductUpsertCommand;
import com.toasty.domain.product.exception.ProductErrorCode;
import com.toasty.domain.product.repository.LiveProductRepository;
import com.toasty.domain.product.repository.ProductImageRepository;
import com.toasty.domain.product.repository.ProductRepository;
import com.toasty.global.config.S3Properties;
import com.toasty.global.exception.CustomException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    private static ProductUpsertCommand upsert(Long productId, String name, String imageObjectKey) {
        return new ProductUpsertCommand(productId, name, 45000, 1, "소가죽 100%", imageObjectKey);
    }

    private LiveProduct givenScheduled(Long productId, Long sellerId, String imageKey) {
        LiveProduct liveProduct = LiveProduct.schedule(LIVE_ID, productId, 0);
        Product product =
                Product.createForLive(sellerId, command("가죽 벨트", "products/pending/7/a.jpg"));
        given(productRepository.findById(productId)).willReturn(java.util.Optional.of(product));
        // 단위 테스트에는 DB가 없어 Product.getId()가 null이다. 사진 조회는 두 경로 모두 같은 상품을 가리킨다.
        List<ProductImage> images =
                List.of(ProductImage.createMain(productId, "https://cdn.example.com/" + imageKey));
        given(productImageRepository.findByProductIdOrderByDisplayOrder(product.getId()))
                .willReturn(images);
        given(productImageRepository.findByProductIdOrderByDisplayOrder(productId))
                .willReturn(images);
        return liveProduct;
    }

    @Nested
    @DisplayName("라이브 수정 - 상품 전체 교체")
    class ReplaceForLive {

        @Test
        @DisplayName("배열에 없는 상품은 편성을 풀고 상품과 사진을 지운 뒤 그 사진 키를 돌려준다")
        void 빠진_상품을_정리한다() {
            LiveProduct dropped = givenScheduled(31L, SELLER_ID, "products/images/7/old.jpg");
            given(liveProductRepository.findByLiveId(LIVE_ID)).willReturn(List.of(dropped));

            List<String> obsolete =
                    productService.replaceForLive(
                            LIVE_ID,
                            SELLER_ID,
                            List.of(upsert(null, "도자기 컵", "products/images/7/b.jpg")),
                            java.util.Collections.singletonList("products/images/7/b.jpg"));

            assertThat(obsolete).containsExactly("products/images/7/old.jpg");
            verify(liveProductRepository).delete(dropped);
            verify(productRepository).deleteById(31L);
        }

        @Test
        @DisplayName("다른 라이브에도 편성된 상품은 편성만 풀고 상품을 지우지 않는다")
        void 다른_라이브의_상품은_남긴다() {
            LiveProduct dropped = givenScheduled(31L, SELLER_ID, "products/images/7/old.jpg");
            given(liveProductRepository.findByLiveId(LIVE_ID)).willReturn(List.of(dropped));
            given(liveProductRepository.existsByProductIdAndLiveIdNot(31L, LIVE_ID))
                    .willReturn(true);

            List<String> obsolete =
                    productService.replaceForLive(LIVE_ID, SELLER_ID, List.of(), List.of());

            assertThat(obsolete).isEmpty();
            verify(liveProductRepository).delete(dropped);
            verify(productRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("배열 순서를 노출 순서로 다시 반영한다")
        void 순서를_다시_매긴다() {
            LiveProduct first = givenScheduled(31L, SELLER_ID, "products/images/7/a.jpg");
            LiveProduct second = LiveProduct.schedule(LIVE_ID, 32L, 1);
            Product other =
                    Product.createForLive(SELLER_ID, command("도자기 컵", "products/pending/7/b.jpg"));
            given(productRepository.findById(32L)).willReturn(java.util.Optional.of(other));
            given(liveProductRepository.findByLiveId(LIVE_ID)).willReturn(List.of(first, second));

            productService.replaceForLive(
                    LIVE_ID,
                    SELLER_ID,
                    List.of(upsert(32L, "도자기 컵", null), upsert(31L, "가죽 벨트", null)),
                    java.util.Arrays.asList(null, null));

            assertThat(second.getDisplayOrder()).isZero();
            assertThat(first.getDisplayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("이 라이브에 편성되지 않은 상품 번호는 PRODUCT_NOT_IN_LIVE다")
        void 편성되지_않은_상품은_거부한다() {
            given(liveProductRepository.findByLiveId(LIVE_ID)).willReturn(List.of());

            assertThatThrownBy(
                            () ->
                                    productService.replaceForLive(
                                            LIVE_ID,
                                            SELLER_ID,
                                            List.of(upsert(99L, "남의 상품", null)),
                                            java.util.Collections.singletonList(null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_IN_LIVE);
        }

        @Test
        @DisplayName("편성돼 있어도 다른 셀러의 상품이면 PRODUCT_NOT_IN_LIVE다")
        void 남의_상품은_거부한다() {
            LiveProduct scheduled = givenScheduled(31L, 99L, "products/images/99/a.jpg");
            given(liveProductRepository.findByLiveId(LIVE_ID)).willReturn(List.of(scheduled));

            assertThatThrownBy(
                            () ->
                                    productService.replaceForLive(
                                            LIVE_ID,
                                            SELLER_ID,
                                            List.of(upsert(31L, "가죽 벨트", null)),
                                            java.util.Collections.singletonList(null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_IN_LIVE);
        }

        @Test
        @DisplayName("사진을 바꾸면 대표 이미지를 갈아끼우고 이전 사진 키를 돌려준다")
        void 사진을_교체한다() {
            LiveProduct scheduled = givenScheduled(31L, SELLER_ID, "products/images/7/old.jpg");
            given(liveProductRepository.findByLiveId(LIVE_ID)).willReturn(List.of(scheduled));

            List<String> obsolete =
                    productService.replaceForLive(
                            LIVE_ID,
                            SELLER_ID,
                            List.of(upsert(31L, "가죽 벨트", "products/pending/7/new.jpg")),
                            List.of("products/images/7/new.jpg"));

            assertThat(obsolete).containsExactly("products/images/7/old.jpg");
        }
    }

    @Nested
    @DisplayName("라이브 수정 - 사진 복사")
    class CopyNewImages {

        @Test
        @DisplayName("사진을 바꾸지 않은 자리는 복사하지 않고 null로 둔다")
        void 바뀐_사진만_복사한다() {
            givenCopySucceeds();

            List<String> copied =
                    productService.copyNewImagesToPermanent(
                            SELLER_ID,
                            List.of(
                                    upsert(31L, "가죽 벨트", null),
                                    upsert(null, "도자기 컵", "products/pending/7/b.jpg")));

            assertThat(copied).containsExactly(null, "products/images/7/b.jpg");
            verify(s3Client, times(1)).copyObject(any(CopyObjectRequest.class));
        }

        @Test
        @DisplayName("새 상품인데 사진이 없으면 PRODUCT_IMAGE_REQUIRED다")
        void 새_상품은_사진이_필수다() {
            assertThatThrownBy(
                            () ->
                                    productService.copyNewImagesToPermanent(
                                            SELLER_ID, List.of(upsert(null, "도자기 컵", null))))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_IMAGE_REQUIRED);
            verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
        }

        @Test
        @DisplayName("남의 사진 키는 PRODUCT_IMAGE_FORBIDDEN이다")
        void 남의_사진은_거부한다() {
            assertThatThrownBy(
                            () ->
                                    productService.copyNewImagesToPermanent(
                                            SELLER_ID,
                                            List.of(
                                                    upsert(
                                                            null,
                                                            "도자기 컵",
                                                            "products/pending/99/b.jpg"))))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_IMAGE_FORBIDDEN);
        }
    }
}
