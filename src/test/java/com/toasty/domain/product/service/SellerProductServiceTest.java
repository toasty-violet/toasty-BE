package com.toasty.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.toasty.domain.live.service.LiveService;
import com.toasty.domain.product.entity.SellerProductUpdateCommand;
import com.toasty.domain.product.exception.ProductErrorCode;
import com.toasty.global.exception.CustomException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("셀러 상품탭 수정·삭제")
class SellerProductServiceTest {

    private static final Long PRODUCT_ID = 31L;
    private static final Long SELLER_ID = 7L;

    private ProductService productService;
    private LiveService liveService;
    private SellerProductService sellerProductService;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        liveService = mock(LiveService.class);
        sellerProductService =
                new SellerProductService(productService, liveService, passthroughTransaction());
    }

    // 콜백을 그대로 실행하는 가짜 트랜잭션. 단위 테스트에는 커밋·롤백이 필요 없다.
    @SuppressWarnings("unchecked")
    private static TransactionTemplate passthroughTransaction() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        given(template.execute(any()))
                .willAnswer(
                        call ->
                                ((org.springframework.transaction.support.TransactionCallback<
                                                        Object>)
                                                call.getArgument(0))
                                        .doInTransaction(
                                                mock(
                                                        org.springframework.transaction
                                                                .TransactionStatus.class)));
        org.mockito.BDDMockito.willAnswer(
                        call -> {
                            ((java.util.function.Consumer<
                                                    org.springframework.transaction
                                                            .TransactionStatus>)
                                            call.getArgument(0))
                                    .accept(
                                            mock(
                                                    org.springframework.transaction
                                                            .TransactionStatus.class));
                            return null;
                        })
                .given(template)
                .executeWithoutResult(any());
        return template;
    }

    private SellerProductUpdateCommand command(String... imageObjectKeys) {
        return new SellerProductUpdateCommand(
                PRODUCT_ID, SELLER_ID, "아이보리 골지 가디건", 29000, 1, "설명", List.of(imageObjectKeys));
    }

    private void givenScheduledIn(Long... liveIds) {
        given(productService.findScheduledLiveIds(PRODUCT_ID, SELLER_ID))
                .willReturn(List.of(liveIds));
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("방송 중인 라이브에 편성돼 있으면 고칠 수 없다")
        void 방송_중이면_거부한다() {
            givenScheduledIn(12L);
            given(liveService.hasBroadcasting(List.of(12L))).willReturn(true);

            assertThatThrownBy(() -> sellerProductService.update(command("k.jpg")))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_BROADCASTING);

            verify(productService, never()).copySellerImagesToPermanent(any(), any());
        }

        @Test
        @DisplayName("고치고 나면 빠진 사진을 커밋 뒤에 지운다")
        void 빠진_사진을_지운다() {
            givenScheduledIn();
            given(productService.copySellerImagesToPermanent(any(), any()))
                    .willReturn(List.of("products/images/7/new.jpg"));
            given(productService.updateSellerProduct(any(), any()))
                    .willReturn(List.of("products/images/7/old.jpg"));

            sellerProductService.update(command("products/pending/7/new.jpg"));

            verify(productService).deleteImagesQuietly(List.of("products/images/7/old.jpg"));
        }

        @Test
        @DisplayName("저장이 실패하면 이번에 복사한 사진만 지운다")
        void 실패하면_복사본을_지운다() {
            givenScheduledIn();
            List<String> permanentKeys = List.of("products/images/7/new.jpg");
            given(productService.copySellerImagesToPermanent(any(), any()))
                    .willReturn(permanentKeys);
            willThrow(new IllegalStateException("저장 실패"))
                    .given(productService)
                    .updateSellerProduct(any(), any());

            assertThatThrownBy(
                            () ->
                                    sellerProductService.update(
                                            command("products/pending/7/new.jpg")))
                    .isInstanceOf(IllegalStateException.class);

            verify(productService)
                    .deleteCopiedImagesQuietly(
                            List.of("products/pending/7/new.jpg"), permanentKeys);
            verify(productService, never()).deleteImagesQuietly(any());
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("라이브에 남은 마지막 상품은 지울 수 없다")
        void 마지막_상품은_거부한다() {
            givenScheduledIn(12L);
            given(productService.hasLiveWithSingleProduct(List.of(12L))).willReturn(true);

            assertThatThrownBy(() -> sellerProductService.delete(PRODUCT_ID, SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_LAST_IN_LIVE);

            verify(productService, never()).deleteSellerProduct(any(), any());
        }

        @Test
        @DisplayName("방송 중인 라이브에 편성돼 있으면 지울 수 없다")
        void 방송_중이면_거부한다() {
            givenScheduledIn(12L);
            given(liveService.hasBroadcasting(List.of(12L))).willReturn(true);

            assertThatThrownBy(() -> sellerProductService.delete(PRODUCT_ID, SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.PRODUCT_BROADCASTING);

            verify(productService, never()).deleteSellerProduct(any(), any());
        }

        @Test
        @DisplayName("지우고 나면 사진을 커밋 뒤에 지운다")
        void 사진을_지운다() {
            givenScheduledIn(12L);
            given(productService.hasLiveWithSingleProduct(List.of(12L))).willReturn(false);
            given(productService.deleteSellerProduct(PRODUCT_ID, SELLER_ID))
                    .willReturn(List.of("products/images/7/a.jpg"));

            sellerProductService.delete(PRODUCT_ID, SELLER_ID);

            assertThat(true).isTrue();
            verify(productService).deleteImagesQuietly(List.of("products/images/7/a.jpg"));
        }
    }
}
