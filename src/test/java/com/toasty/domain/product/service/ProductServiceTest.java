package com.toasty.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.toasty.domain.product.controller.dto.response.LiveProductResponse;
import com.toasty.domain.product.controller.dto.response.LiveProductsResponse;
import com.toasty.domain.product.controller.dto.response.ProductDetailResponse;
import com.toasty.domain.product.controller.dto.response.SellerProductsResponse;
import com.toasty.domain.product.controller.dto.response.StoreProductResponse;
import com.toasty.domain.product.controller.dto.response.StoreProductsResponse;
import com.toasty.domain.product.entity.LiveProduct;
import com.toasty.domain.product.entity.LiveProductPinCommand;
import com.toasty.domain.product.entity.LiveProductStatus;
import com.toasty.domain.product.entity.LiveProductUpdateCommand;
import com.toasty.domain.product.entity.Product;
import com.toasty.domain.product.entity.ProductCreateCommand;
import com.toasty.domain.product.entity.ProductImage;
import com.toasty.domain.product.entity.ProductUpsertCommand;
import com.toasty.domain.product.entity.SalesType;
import com.toasty.domain.product.entity.SellerProductFilter;
import com.toasty.domain.product.entity.SellerProductPageCommand;
import com.toasty.domain.product.entity.SellerProductUpdateCommand;
import com.toasty.domain.product.entity.StoreProductPageCommand;
import com.toasty.domain.product.exception.ProductErrorCode;
import com.toasty.domain.product.repository.LiveProductRepository;
import com.toasty.domain.product.repository.ProductImageRepository;
import com.toasty.domain.product.repository.ProductRepository;
import com.toasty.domain.product.repository.SellerProductCount;
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
        given(productImageRepository.findByProductIdInOrderByDisplayOrder(List.of(productId)))
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
            given(liveProductRepository.findByLiveIdOrderByDisplayOrder(LIVE_ID))
                    .willReturn(List.of(dropped));

            List<String> obsolete =
                    productService.replaceForLive(
                            LIVE_ID,
                            SELLER_ID,
                            List.of(upsert(null, "도자기 컵", "products/images/7/b.jpg")),
                            java.util.Collections.singletonList("products/images/7/b.jpg"));

            assertThat(obsolete).containsExactly("products/images/7/old.jpg");
            verify(liveProductRepository).deleteAllInBatch(List.of(dropped));
            verify(productRepository).deleteAllByIdInBatch(List.of(31L));
        }

        @Test
        @DisplayName("다른 라이브에도 편성된 상품은 편성만 풀고 상품을 지우지 않는다")
        void 다른_라이브의_상품은_남긴다() {
            LiveProduct dropped = givenScheduled(31L, SELLER_ID, "products/images/7/old.jpg");
            given(liveProductRepository.findByLiveIdOrderByDisplayOrder(LIVE_ID))
                    .willReturn(List.of(dropped));
            given(liveProductRepository.findProductIdsScheduledInOtherLives(List.of(31L), LIVE_ID))
                    .willReturn(List.of(31L));

            List<String> obsolete =
                    productService.replaceForLive(LIVE_ID, SELLER_ID, List.of(), List.of());

            assertThat(obsolete).isEmpty();
            verify(liveProductRepository).deleteAllInBatch(List.of(dropped));
            verify(productRepository).deleteAllByIdInBatch(List.of());
        }

        @Test
        @DisplayName("배열 순서를 노출 순서로 다시 반영한다")
        void 순서를_다시_매긴다() {
            LiveProduct first = givenScheduled(31L, SELLER_ID, "products/images/7/a.jpg");
            LiveProduct second = LiveProduct.schedule(LIVE_ID, 32L, 1);
            Product other =
                    Product.createForLive(SELLER_ID, command("도자기 컵", "products/pending/7/b.jpg"));
            given(productRepository.findById(32L)).willReturn(java.util.Optional.of(other));
            given(liveProductRepository.findByLiveIdOrderByDisplayOrder(LIVE_ID))
                    .willReturn(List.of(first, second));

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
            given(liveProductRepository.findByLiveIdOrderByDisplayOrder(LIVE_ID))
                    .willReturn(List.of());

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
            given(liveProductRepository.findByLiveIdOrderByDisplayOrder(LIVE_ID))
                    .willReturn(List.of(scheduled));

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
        @DisplayName("같은 상품을 두 번 보내면 PRODUCT_DUPLICATED다")
        void 중복된_상품은_거부한다() {
            assertThatThrownBy(
                            () ->
                                    productService.replaceForLive(
                                            LIVE_ID,
                                            SELLER_ID,
                                            List.of(
                                                    upsert(31L, "가죽 벨트", null),
                                                    upsert(31L, "가죽 벨트 수정", null)),
                                            java.util.Arrays.asList(null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_DUPLICATED);
            verify(liveProductRepository, never()).findByLiveIdOrderByDisplayOrder(any());
        }

        @Test
        @DisplayName("사진을 바꾸면 대표 이미지를 갈아끼우고 이전 사진 키를 돌려준다")
        void 사진을_교체한다() {
            LiveProduct scheduled = givenScheduled(31L, SELLER_ID, "products/images/7/old.jpg");
            given(liveProductRepository.findByLiveIdOrderByDisplayOrder(LIVE_ID))
                    .willReturn(List.of(scheduled));

            List<String> obsolete =
                    productService.replaceForLive(
                            LIVE_ID,
                            SELLER_ID,
                            List.of(upsert(31L, "가죽 벨트", "products/pending/7/new.jpg")),
                            List.of("products/images/7/new.jpg"));

            assertThat(obsolete).containsExactly("products/images/7/old.jpg");
        }
    }

    @Test
    @DisplayName("사진 정리가 S3에서 실패해도 예외를 밖으로 내보내지 않는다")
    void 사진_정리_실패를_삼킨다() {
        willThrow(S3Exception.builder().message("서버 오류").build())
                .given(s3Client)
                .deleteObject(any(DeleteObjectRequest.class));

        assertThatCode(
                        () ->
                                productService.deleteImagesQuietly(
                                        List.of("products/images/7/old.jpg")))
                .doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("방송 중 상품 관리")
    class DuringBroadcast {

        private LiveProduct givenScheduledWithStock(Long productId, int stock) {
            LiveProduct liveProduct = LiveProduct.schedule(LIVE_ID, productId, 0);
            Product product =
                    Product.createForLive(
                            SELLER_ID,
                            new ProductCreateCommand("가죽 벨트", 45000, stock, null, "k.jpg"));
            given(productRepository.findById(productId)).willReturn(java.util.Optional.of(product));
            given(liveProductRepository.findByLiveIdAndProductId(LIVE_ID, productId))
                    .willReturn(java.util.Optional.of(liveProduct));
            return liveProduct;
        }

        @Test
        @DisplayName("고정하면 구매 가능해지고 고정 시각이 찍힌다")
        void 고정하면_구매_가능해진다() {
            LiveProduct liveProduct = givenScheduledWithStock(31L, 1);

            productService.pinForLive(new LiveProductPinCommand(LIVE_ID, 31L, SELLER_ID));

            assertThat(liveProduct.getStatus()).isEqualTo(LiveProductStatus.ACTIVE);
            assertThat(liveProduct.getPinnedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 고정했던 상품을 다시 고정하면 구매 가능 상태는 그대로고 시각만 갱신된다")
        void 다시_고정해도_구매_가능은_유지된다() {
            LiveProduct liveProduct = givenScheduledWithStock(31L, 1);
            productService.pinForLive(new LiveProductPinCommand(LIVE_ID, 31L, SELLER_ID));
            java.time.LocalDateTime first = liveProduct.getPinnedAt();

            productService.pinForLive(new LiveProductPinCommand(LIVE_ID, 31L, SELLER_ID));

            assertThat(liveProduct.getStatus()).isEqualTo(LiveProductStatus.ACTIVE);
            assertThat(liveProduct.getPinnedAt()).isAfterOrEqualTo(first);
        }

        @Test
        @DisplayName("품절된 상품은 고정할 수 없다")
        void 품절된_상품은_고정할_수_없다() {
            LiveProduct liveProduct = givenScheduledWithStock(31L, 0);

            assertThatThrownBy(
                            () ->
                                    productService.pinForLive(
                                            new LiveProductPinCommand(LIVE_ID, 31L, SELLER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_OUT_OF_STOCK);
            assertThat(liveProduct.getStatus()).isEqualTo(LiveProductStatus.SCHEDULED);
        }

        @Test
        @DisplayName("이 라이브에 편성되지 않은 상품은 고정할 수 없다")
        void 편성되지_않은_상품은_고정할_수_없다() {
            given(liveProductRepository.findByLiveIdAndProductId(LIVE_ID, 99L))
                    .willReturn(java.util.Optional.empty());

            assertThatThrownBy(
                            () ->
                                    productService.pinForLive(
                                            new LiveProductPinCommand(LIVE_ID, 99L, SELLER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_IN_LIVE);
        }

        @Test
        @DisplayName("시트를 만들 때 가장 최근에 고정한 상품을 현재 고정 상품으로 고른다")
        void 현재_고정_상품을_고른다() {
            LiveProduct first = LiveProduct.schedule(LIVE_ID, 31L, 0);
            LiveProduct second = LiveProduct.schedule(LIVE_ID, 32L, 1);
            first.pin(java.time.LocalDateTime.now().minusMinutes(5));
            second.pin(java.time.LocalDateTime.now());
            givenSheet(List.of(first, second));

            assertThat(productService.findLiveProducts(LIVE_ID).currentPinnedProductId())
                    .isEqualTo(32L);
        }

        @Test
        @DisplayName("아무것도 고정하지 않았으면 현재 고정 상품이 없다")
        void 고정한_적이_없으면_null이다() {
            givenSheet(List.of(LiveProduct.schedule(LIVE_ID, 31L, 0)));

            LiveProductsResponse response = productService.findLiveProducts(LIVE_ID);

            assertThat(response.currentPinnedProductId()).isNull();
            assertThat(response.products()).hasSize(1);
        }

        @Test
        @DisplayName("편성이 없으면 현재 고정 상품도 목록도 비어 있다")
        void 편성이_없으면_비어_있다() {
            givenSheet(List.of());

            LiveProductsResponse response = productService.findLiveProducts(LIVE_ID);

            assertThat(response.currentPinnedProductId()).isNull();
            assertThat(response.products()).isEmpty();
        }

        private void givenSheet(List<LiveProduct> scheduled) {
            given(liveProductRepository.findByLiveIdOrderByDisplayOrder(LIVE_ID))
                    .willReturn(scheduled);
            given(productRepository.findAllById(any()))
                    .willReturn(
                            scheduled.stream()
                                    .map(
                                            liveProduct ->
                                                    product(liveProduct.getProductId(), "가죽 벨트"))
                                    .toList());
            given(productImageRepository.findByProductIdInOrderByDisplayOrder(any()))
                    .willReturn(List.of());
        }

        private Product product(Long productId, String name) {
            Product product =
                    Product.createForLive(
                            SELLER_ID, new ProductCreateCommand(name, 45000, 1, null, "k.jpg"));
            org.springframework.test.util.ReflectionTestUtils.setField(product, "id", productId);
            return product;
        }

        @Test
        @DisplayName("방송 중에는 가격과 재고만 바뀐다")
        void 가격과_재고를_고친다() {
            givenScheduledWithStock(31L, 1);

            productService.changePriceAndStockDuringLive(
                    new LiveProductUpdateCommand(LIVE_ID, 31L, SELLER_ID, 39000, 5));

            Product product = productRepository.findById(31L).orElseThrow();
            assertThat(product.getPrice()).isEqualTo(39000);
            assertThat(product.getStockQuantity()).isEqualTo(5);
            assertThat(product.getName()).isEqualTo("가죽 벨트");
        }

        @Test
        @DisplayName("이 라이브에 편성되지 않은 상품은 가격을 고칠 수 없다")
        void 편성되지_않은_상품은_고칠_수_없다() {
            given(liveProductRepository.findByLiveIdAndProductId(LIVE_ID, 99L))
                    .willReturn(java.util.Optional.empty());

            assertThatThrownBy(
                            () ->
                                    productService.changePriceAndStockDuringLive(
                                            new LiveProductUpdateCommand(
                                                    LIVE_ID, 99L, SELLER_ID, 32000, 3)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_IN_LIVE);

            verify(productRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("셀러 상품탭 목록")
    class FindSellerProducts {

        private static final int PAGE_SIZE = 20;

        private List<Product> products(int count) {
            return java.util.stream.IntStream.rangeClosed(1, count)
                    .mapToObj(i -> product((long) i, "상품" + i))
                    .toList();
        }

        private Product product(Long productId, String name) {
            Product product =
                    Product.createForLive(
                            SELLER_ID, new ProductCreateCommand(name, 12000, 1, null, "k.jpg"));
            org.springframework.test.util.ReflectionTestUtils.setField(product, "id", productId);
            return product;
        }

        private void givenFound(List<Product> found) {
            given(
                            productRepository
                                    .findBySellerIdAndSalesTypeNotAndNameContainingAndIdLessThanOrderByIdDesc(
                                            any(), any(), any(), any(), any()))
                    .willReturn(found);
            given(productImageRepository.findByProductIdInOrderByDisplayOrder(any()))
                    .willReturn(List.of());
        }

        @Test
        @DisplayName("첫 요청에는 상태 칩 건수를 함께 내린다")
        void 첫_요청은_건수를_준다() {
            givenFound(products(2));
            given(productRepository.countBySalesType(any(), any(), any()))
                    .willReturn(List.of(count(SalesType.GENERAL, 8), count(SalesType.LIVE, 24)));

            SellerProductsResponse response =
                    productService.findSellerProducts(
                            new SellerProductPageCommand(
                                    SELLER_ID, SellerProductFilter.ALL, null, null));

            assertThat(response.counts().all()).isEqualTo(32);
            assertThat(response.counts().onSale()).isEqualTo(8);
            assertThat(response.counts().scheduled()).isEqualTo(24);
        }

        @Test
        @DisplayName("이어 받을 때는 건수를 세지 않는다")
        void 이어_받으면_건수를_안_센다() {
            givenFound(products(2));

            SellerProductsResponse response =
                    productService.findSellerProducts(
                            new SellerProductPageCommand(
                                    SELLER_ID, SellerProductFilter.ALL, 30L, null));

            assertThat(response.counts()).isNull();
            verify(productRepository, never()).countBySalesType(any(), any(), any());
        }

        @Test
        @DisplayName("한 장을 더 읽어 다음이 있는지 보고, 넘치는 건 잘라낸다")
        void 다음_페이지를_안다() {
            givenFound(products(PAGE_SIZE + 1));
            given(productRepository.countBySalesType(any(), any(), any())).willReturn(List.of());

            SellerProductsResponse response =
                    productService.findSellerProducts(
                            new SellerProductPageCommand(
                                    SELLER_ID, SellerProductFilter.ALL, null, null));

            assertThat(response.items()).hasSize(PAGE_SIZE);
            assertThat(response.hasNext()).isTrue();
            assertThat(response.nextCursor()).isEqualTo(PAGE_SIZE);
        }

        @Test
        @DisplayName("마지막 묶음이면 다음이 없다")
        void 마지막이면_끝이다() {
            givenFound(products(3));
            given(productRepository.countBySalesType(any(), any(), any())).willReturn(List.of());

            SellerProductsResponse response =
                    productService.findSellerProducts(
                            new SellerProductPageCommand(
                                    SELLER_ID, SellerProductFilter.ALL, null, null));

            assertThat(response.items()).hasSize(3);
            assertThat(response.hasNext()).isFalse();
            assertThat(response.nextCursor()).isNull();
        }

        @Test
        @DisplayName("상품이 없으면 커서를 주지 않는다")
        void 비어_있으면_커서가_없다() {
            givenFound(List.of());
            given(productRepository.countBySalesType(any(), any(), any())).willReturn(List.of());

            SellerProductsResponse response =
                    productService.findSellerProducts(
                            new SellerProductPageCommand(
                                    SELLER_ID, SellerProductFilter.ALL, null, null));

            assertThat(response.items()).isEmpty();
            assertThat(response.nextCursor()).isNull();
            assertThat(response.hasNext()).isFalse();
        }

        @Test
        @DisplayName("검색어가 없으면 빈 문자열로 넘겨 전체를 읽는다")
        void 검색어가_없으면_전체다() {
            givenFound(List.of());
            given(productRepository.countBySalesType(any(), any(), any())).willReturn(List.of());

            productService.findSellerProducts(
                    new SellerProductPageCommand(SELLER_ID, SellerProductFilter.ALL, null, null));

            verify(productRepository)
                    .findBySellerIdAndSalesTypeNotAndNameContainingAndIdLessThanOrderByIdDesc(
                            any(), any(), eq(""), any(), any());
        }

        @Test
        @DisplayName("검색 중이면 건수도 그 검색어 안에서 센다")
        void 검색어로_건수를_센다() {
            givenFound(List.of());
            given(productRepository.countBySalesType(any(), any(), any())).willReturn(List.of());

            productService.findSellerProducts(
                    new SellerProductPageCommand(SELLER_ID, SellerProductFilter.ALL, null, "가디건"));

            verify(productRepository).countBySalesType(any(), any(), eq("가디건"));
            verify(productRepository)
                    .findBySellerIdAndSalesTypeNotAndNameContainingAndIdLessThanOrderByIdDesc(
                            any(), any(), eq("가디건"), any(), any());
        }

        @Test
        @DisplayName("어느 칩으로도 다 팔린 상품은 나오지 않는다")
        void 품절은_어디에도_없다() {
            assertThat(SellerProductFilter.EXCLUDED).isEqualTo(SalesType.SOLD_OUT);
            assertThat(SellerProductFilter.values())
                    .allSatisfy(
                            filter ->
                                    assertThat(filter.salesType())
                                            .isNotEqualTo(SalesType.SOLD_OUT));
        }

        private SellerProductCount count(SalesType salesType, int productCount) {
            return new SellerProductCount() {
                @Override
                public SalesType getSalesType() {
                    return salesType;
                }

                @Override
                public int getProductCount() {
                    return productCount;
                }
            };
        }
    }

    @Nested
    @DisplayName("셀러 상품탭 수정")
    class UpdateSellerProduct {

        private static final Long PRODUCT_ID = 31L;
        private static final String BASE = "https://cdn.example.com/";

        private void givenOwnProductWithImages(String... objectKeys) {
            Product product =
                    Product.createForLive(
                            SELLER_ID, new ProductCreateCommand("가디건", 29000, 1, null, "k.jpg"));
            org.springframework.test.util.ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
            given(productRepository.findById(PRODUCT_ID))
                    .willReturn(java.util.Optional.of(product));
            given(productImageRepository.findByProductIdOrderByDisplayOrder(PRODUCT_ID))
                    .willReturn(
                            java.util.Arrays.stream(objectKeys)
                                    .map(key -> ProductImage.create(PRODUCT_ID, BASE + key, 0))
                                    .toList());
        }

        private SellerProductUpdateCommand command(String... objectKeys) {
            return new SellerProductUpdateCommand(
                    PRODUCT_ID, SELLER_ID, "가디건", 29000, 1, "설명", List.of(objectKeys));
        }

        @Test
        @DisplayName("목록에서 빠진 사진을 지울 대상으로 돌려준다")
        void 빠진_사진을_돌려준다() {
            givenOwnProductWithImages("products/images/7/a.jpg", "products/images/7/b.jpg");

            List<String> obsolete =
                    productService.updateSellerProduct(
                            command("products/images/7/b.jpg"), List.of("products/images/7/b.jpg"));

            assertThat(obsolete).containsExactly("products/images/7/a.jpg");
        }

        @Test
        @DisplayName("이 상품 것이 아닌 확정 사진은 붙일 수 없다")
        void 남의_사진은_붙일_수_없다() {
            givenOwnProductWithImages("products/images/7/a.jpg");

            assertThatThrownBy(
                            () ->
                                    productService.updateSellerProduct(
                                            command("products/images/9/other.jpg"),
                                            List.of("products/images/9/other.jpg")))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_IMAGE_FORBIDDEN);

            verify(productImageRepository, never()).deleteAllInBatch(any());
        }

        @Test
        @DisplayName("남의 상품이면 PRODUCT_NOT_FOUND다")
        void 남의_상품은_고칠_수_없다() {
            givenOwnProductWithImages("products/images/7/a.jpg");

            assertThatThrownBy(
                            () ->
                                    productService.updateSellerProduct(
                                            new SellerProductUpdateCommand(
                                                    PRODUCT_ID,
                                                    99L,
                                                    "가디건",
                                                    29000,
                                                    1,
                                                    null,
                                                    List.of("products/images/7/a.jpg")),
                                            List.of("products/images/7/a.jpg")))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("구매자 상품 상세")
    class FindProductDetail {

        private static final Long PRODUCT_ID = 31L;

        private Product product(Long productId, SalesType salesType, int stock) {
            Product product =
                    Product.createForLive(
                            SELLER_ID,
                            new ProductCreateCommand("가디건", 29000, stock, "설명", "k.jpg"));
            org.springframework.test.util.ReflectionTestUtils.setField(product, "id", productId);
            org.springframework.test.util.ReflectionTestUtils.setField(
                    product, "salesType", salesType);
            return product;
        }

        private void givenProduct(Product product) {
            given(productRepository.findByIdAndSalesType(PRODUCT_ID, SalesType.GENERAL))
                    .willReturn(java.util.Optional.of(product));
            given(productImageRepository.findByProductIdOrderByDisplayOrder(PRODUCT_ID))
                    .willReturn(
                            List.of(
                                    ProductImage.createMain(PRODUCT_ID, "https://cdn/a.jpg"),
                                    ProductImage.createMain(PRODUCT_ID, "https://cdn/b.jpg")));
            given(productImageRepository.findByProductIdInOrderByDisplayOrder(any()))
                    .willReturn(List.of());
        }

        private void givenSellerHas(Product... products) {
            given(
                            productRepository.findBySellerIdAndSalesTypeAndIdLessThanOrderByIdDesc(
                                    any(), any(), any(), any()))
                    .willReturn(List.of(products));
        }

        @Test
        @DisplayName("사진을 노출 순서대로 준다")
        void 사진을_순서대로_준다() {
            givenProduct(product(PRODUCT_ID, SalesType.GENERAL, 1));
            givenSellerHas();

            ProductDetailResponse detail = productService.findProductDetail(PRODUCT_ID);

            assertThat(detail.imageUrls())
                    .containsExactly("https://cdn/a.jpg", "https://cdn/b.jpg");
            assertThat(detail.sellerId()).isEqualTo(SELLER_ID);
        }

        @Test
        @DisplayName("다른 상품에서 자기 자신은 빠진다")
        void 자기는_빠진다() {
            Product self = product(PRODUCT_ID, SalesType.GENERAL, 1);
            givenProduct(self);
            givenSellerHas(
                    self,
                    product(32L, SalesType.GENERAL, 1),
                    product(33L, SalesType.GENERAL, 1),
                    product(34L, SalesType.GENERAL, 1));

            ProductDetailResponse detail = productService.findProductDetail(PRODUCT_ID);

            assertThat(detail.otherProducts())
                    .extracting(StoreProductResponse::productId)
                    .containsExactly(32L, 33L, 34L);
        }

        @Test
        @DisplayName("다른 상품은 세 개까지만 준다")
        void 세_개까지만_준다() {
            Product self = product(PRODUCT_ID, SalesType.GENERAL, 1);
            givenProduct(self);
            givenSellerHas(
                    product(32L, SalesType.GENERAL, 1),
                    product(33L, SalesType.GENERAL, 1),
                    product(34L, SalesType.GENERAL, 1),
                    product(35L, SalesType.GENERAL, 1));

            assertThat(productService.findProductDetail(PRODUCT_ID).otherProducts()).hasSize(3);
        }

        @Test
        @DisplayName("판매중이 아니면 열리지 않는다")
        void 판매중이_아니면_안_열린다() {
            given(productRepository.findByIdAndSalesType(PRODUCT_ID, SalesType.GENERAL))
                    .willReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> productService.findProductDetail(PRODUCT_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        @Test
        @DisplayName("없는 상품이면 PRODUCT_NOT_FOUND다")
        void 없으면_찾을_수_없다() {
            given(productRepository.findByIdAndSalesType(PRODUCT_ID, SalesType.GENERAL))
                    .willReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> productService.findProductDetail(PRODUCT_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("스토어 상품 그리드")
    class FindStoreProducts {

        private static final int PAGE_SIZE = 20;

        private List<Product> products(int count) {
            return java.util.stream.IntStream.rangeClosed(1, count)
                    .mapToObj(
                            i -> {
                                Product product =
                                        Product.createForLive(
                                                SELLER_ID,
                                                new ProductCreateCommand(
                                                        "상품" + i, 12000, 1, null, "k.jpg"));
                                org.springframework.test.util.ReflectionTestUtils.setField(
                                        product, "id", (long) i);
                                return product;
                            })
                    .toList();
        }

        private void givenFound(List<Product> found) {
            given(
                            productRepository.findBySellerIdAndSalesTypeAndIdLessThanOrderByIdDesc(
                                    any(), any(), any(), any()))
                    .willReturn(found);
            given(productImageRepository.findByProductIdInOrderByDisplayOrder(any()))
                    .willReturn(List.of());
        }

        @Test
        @DisplayName("판매중인 상품만 읽는다")
        void 살_수_있는_것만_읽는다() {
            givenFound(products(2));

            productService.findStoreProducts(new StoreProductPageCommand(SELLER_ID, null));

            verify(productRepository)
                    .findBySellerIdAndSalesTypeAndIdLessThanOrderByIdDesc(
                            eq(SELLER_ID), eq(SalesType.GENERAL), any(), any());
        }

        @Test
        @DisplayName("한 장을 더 읽어 다음이 있는지 보고, 넘치는 건 잘라낸다")
        void 다음_페이지를_안다() {
            givenFound(products(PAGE_SIZE + 1));

            StoreProductsResponse response =
                    productService.findStoreProducts(new StoreProductPageCommand(SELLER_ID, null));

            assertThat(response.items()).hasSize(PAGE_SIZE);
            assertThat(response.hasNext()).isTrue();
            assertThat(response.nextCursor()).isEqualTo(PAGE_SIZE);
        }

        @Test
        @DisplayName("마지막 묶음이면 커서를 주지 않는다")
        void 마지막이면_커서가_없다() {
            givenFound(products(3));

            StoreProductsResponse response =
                    productService.findStoreProducts(new StoreProductPageCommand(SELLER_ID, null));

            assertThat(response.items()).hasSize(3);
            assertThat(response.hasNext()).isFalse();
            assertThat(response.nextCursor()).isNull();
        }

        @Test
        @DisplayName("상품이 없으면 사진도 읽지 않는다")
        void 비어_있으면_사진을_안_읽는다() {
            given(
                            productRepository.findBySellerIdAndSalesTypeAndIdLessThanOrderByIdDesc(
                                    any(), any(), any(), any()))
                    .willReturn(List.of());

            StoreProductsResponse response =
                    productService.findStoreProducts(new StoreProductPageCommand(SELLER_ID, null));

            assertThat(response.items()).isEmpty();
            verify(productImageRepository, never()).findByProductIdInOrderByDisplayOrder(any());
        }
    }

    @Nested
    @DisplayName("라이브 종료 - 판매 방식 정리")
    class CloseLiveSales {

        private Product product(Long productId, String name, int stockQuantity) {
            Product product =
                    Product.createForLive(
                            SELLER_ID,
                            new ProductCreateCommand(name, 45000, stockQuantity, null, "k.jpg"));
            org.springframework.test.util.ReflectionTestUtils.setField(product, "id", productId);
            return product;
        }

        private void givenScheduled(Product... products) {
            given(liveProductRepository.findByLiveIdOrderByDisplayOrder(LIVE_ID))
                    .willReturn(
                            java.util.Arrays.stream(products)
                                    .map(p -> LiveProduct.schedule(LIVE_ID, p.getId(), 0))
                                    .toList());
            given(productRepository.findAllById(any())).willReturn(List.of(products));
        }

        @Test
        @DisplayName("재고가 남으면 일반판매로, 다 팔렸으면 판매를 닫는다")
        void 재고에_따라_나뉜다() {
            Product remaining = product(31L, "가죽 벨트", 2);
            Product soldOut = product(32L, "도자기 컵", 0);
            givenScheduled(remaining, soldOut);

            productService.closeLiveSales(LIVE_ID);

            assertThat(remaining.getSalesType()).isEqualTo(SalesType.GENERAL);
            assertThat(soldOut.getSalesType()).isEqualTo(SalesType.SOLD_OUT);
        }

        @Test
        @DisplayName("이미 넘어간 상품은 다시 바뀌지 않는다")
        void 두_번_넘기지_않는다() {
            Product product = product(31L, "가죽 벨트", 2);
            product.closeLiveSales();
            product.changePriceAndStock(45000, 0);
            givenScheduled(product);

            productService.closeLiveSales(LIVE_ID);

            assertThat(product.getSalesType()).isEqualTo(SalesType.GENERAL);
        }

        @Test
        @DisplayName("편성이 비어 있으면 상품을 읽지 않는다")
        void 편성이_없으면_읽지_않는다() {
            given(liveProductRepository.findByLiveIdOrderByDisplayOrder(LIVE_ID))
                    .willReturn(List.of());

            productService.closeLiveSales(LIVE_ID);

            verify(productRepository, never()).findAllById(any());
        }
    }

    @Nested
    @DisplayName("라이브 삭제 - 편성 상품 정리")
    class RemoveAllForLive {

        @Test
        @DisplayName("편성과 상품·사진을 모두 지우고 지울 사진 키를 돌려준다")
        void 전부_정리한다() {
            LiveProduct first = givenScheduled(31L, SELLER_ID, "products/images/7/a.jpg");
            given(liveProductRepository.findByLiveIdOrderByDisplayOrder(LIVE_ID))
                    .willReturn(List.of(first));

            List<String> obsolete = productService.removeAllForLive(LIVE_ID);

            assertThat(obsolete).containsExactly("products/images/7/a.jpg");
            verify(liveProductRepository).deleteAllInBatch(List.of(first));
            verify(productRepository).deleteAllByIdInBatch(List.of(31L));
        }

        @Test
        @DisplayName("다른 라이브에도 편성된 상품은 편성만 풀고 남긴다")
        void 다른_라이브의_상품은_남긴다() {
            LiveProduct first = givenScheduled(31L, SELLER_ID, "products/images/7/a.jpg");
            given(liveProductRepository.findByLiveIdOrderByDisplayOrder(LIVE_ID))
                    .willReturn(List.of(first));
            given(liveProductRepository.findProductIdsScheduledInOtherLives(List.of(31L), LIVE_ID))
                    .willReturn(List.of(31L));

            List<String> obsolete = productService.removeAllForLive(LIVE_ID);

            assertThat(obsolete).isEmpty();
            verify(liveProductRepository).deleteAllInBatch(List.of(first));
            verify(productRepository).deleteAllByIdInBatch(List.of());
        }

        @Test
        @DisplayName("편성된 상품이 없으면 아무것도 지우지 않는다")
        void 편성이_없으면_아무것도_안_한다() {
            given(liveProductRepository.findByLiveIdOrderByDisplayOrder(LIVE_ID))
                    .willReturn(List.of());

            assertThat(productService.removeAllForLive(LIVE_ID)).isEmpty();

            verify(productRepository, never()).deleteById(any());
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
