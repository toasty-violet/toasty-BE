package com.toasty.domain.live.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.toasty.domain.live.client.FakeLiveStreamingClient;
import com.toasty.domain.live.client.dto.StreamState;
import com.toasty.domain.live.controller.dto.response.BroadcastCredentialResponse;
import com.toasty.domain.live.controller.dto.response.LiveDetailResponse;
import com.toasty.domain.live.controller.dto.response.LivePlaybackResponse;
import com.toasty.domain.live.controller.dto.response.LiveProductsResponse;
import com.toasty.domain.live.controller.dto.response.LiveStreamStatusResponse;
import com.toasty.domain.live.controller.dto.response.LiveWithProductsResponse;
import com.toasty.domain.live.controller.dto.response.SellerLiveTabResponse;
import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveCreateCommand;
import com.toasty.domain.live.entity.LiveStatus;
import com.toasty.domain.live.entity.LiveUpdateCommand;
import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.domain.live.repository.LiveRepository;
import com.toasty.global.exception.CustomException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class LiveServiceTest {

    private static final Long SELLER_ID = 7L;

    private LiveRepository liveRepository;
    private FakeLiveStreamingClient streamingClient;
    private com.toasty.domain.product.service.ProductService productService;
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private LiveService liveService;

    @BeforeEach
    void setUp() {
        liveRepository = mock(LiveRepository.class);
        streamingClient = new FakeLiveStreamingClient();
        productService = mock(com.toasty.domain.product.service.ProductService.class);
        transactionTemplate = passthroughTransaction();
        liveService =
                new LiveService(
                        liveRepository, streamingClient, productService, transactionTemplate);
    }

    // 콜백을 그대로 실행하는 가짜 트랜잭션. 단위 테스트에는 커밋·롤백이 필요 없다.
    @SuppressWarnings("unchecked")
    private static org.springframework.transaction.support.TransactionTemplate
            passthroughTransaction() {
        var template = mock(org.springframework.transaction.support.TransactionTemplate.class);
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
        return template;
    }

    private static LiveCreateCommand command() {
        return new LiveCreateCommand(
                SELLER_ID,
                "빈티지 여름옷 라이브",
                "여름 상품을 소개합니다",
                java.time.LocalDateTime.now().plusDays(1),
                java.util.List.of(
                        new com.toasty.domain.product.entity.ProductCreateCommand(
                                "핸드메이드 가죽 벨트",
                                45000,
                                1,
                                null,
                                "products/pending/7/2026/09/02/a.jpg")));
    }

    private void givenSaveSucceeds() {
        given(liveRepository.save(any(Live.class))).willAnswer(call -> call.getArgument(0));
    }

    private Live givenLive(Long liveId) {
        Live live = Live.create(command(), "arn:aws:ivs:channel/abc", "https://playback/abc.m3u8");
        given(liveRepository.findById(liveId)).willReturn(Optional.of(live));
        return live;
    }

    private Live givenLiveByPublicId(String publicId) {
        Live live = Live.create(command(), "arn:aws:ivs:channel/abc", "https://playback/abc.m3u8");
        given(liveRepository.findByPublicId(publicId)).willReturn(Optional.of(live));
        return live;
    }

    @Nested
    @DisplayName("라이브 생성")
    class Create {

        @Test
        @DisplayName("채널을 만들고 READY 상태로 저장한 뒤 최초 송출정보를 함께 반환한다")
        void 채널을_만들고_READY로_저장한다() {
            givenSaveSucceeds();

            LiveWithProductsResponse response = liveService.create(command());

            assertThat(response.live().sellerId()).isEqualTo(SELLER_ID);
            assertThat(response.live().title()).isEqualTo("빈티지 여름옷 라이브");
            assertThat(response.live().status()).isEqualTo(LiveStatus.READY);
            assertThat(response.live().publicId()).isNotBlank();
            assertThat(response.live().scheduledAt()).isNotNull();
            assertThat(streamingClient.deletedChannelArns()).isEmpty();
        }

        @Test
        @DisplayName("함께 보낸 상품을 그 라이브에 등록하고 응답에 담는다")
        void 상품을_함께_등록한다() {
            givenSaveSucceeds();
            given(productService.registerForLive(any(), any(), any(), any()))
                    .willReturn(
                            java.util.List.of(
                                    new com.toasty.domain.product.controller.dto.response
                                            .LiveProductResponse(
                                            31L,
                                            44L,
                                            "핸드메이드 가죽 벨트",
                                            45000,
                                            1,
                                            "https://cdn.example.com/a.jpg",
                                            0,
                                            com.toasty.domain.product.entity.LiveProductStatus
                                                    .SCHEDULED)));

            LiveWithProductsResponse response = liveService.create(command());

            assertThat(response.products()).hasSize(1);
            assertThat(response.products().get(0).name()).isEqualTo("핸드메이드 가죽 벨트");
            assertThat(streamingClient.deletedChannelArns()).isEmpty();
        }

        @Test
        @DisplayName("상품 등록이 실패하면 이미 만든 IVS 채널과 복사한 사진을 지운다")
        void 상품_등록_실패시_채널과_사진을_지운다() {
            givenSaveSucceeds();
            List<String> imageKeys = List.of("products/images/44/a.jpg");
            given(productService.copyImagesToPermanent(any(), any())).willReturn(imageKeys);
            given(productService.registerForLive(any(), any(), any(), any()))
                    .willThrow(
                            new CustomException(
                                    com.toasty.domain.product.exception.ProductErrorCode
                                            .PRODUCT_IMAGE_NOT_UPLOADED));

            assertThatThrownBy(() -> liveService.create(command()))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(
                            com.toasty.domain.product.exception.ProductErrorCode
                                    .PRODUCT_IMAGE_NOT_UPLOADED);

            assertThat(streamingClient.deletedChannelArns()).hasSize(1);
            verify(productService).deleteImagesQuietly(imageKeys);
        }

        @Test
        @DisplayName("예정된 라이브가 상한이면 거부하고 사진도 채널도 건드리지 않는다")
        void 예정_라이브가_상한이면_거부한다() {
            given(liveRepository.countBySellerIdAndStatus(SELLER_ID, LiveStatus.READY))
                    .willReturn(10);

            assertThatThrownBy(() -> liveService.create(command()))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_SCHEDULE_LIMIT_EXCEEDED);

            verify(productService, never()).copyImagesToPermanent(any(), any());
            assertThat(streamingClient.createdChannelNames()).isEmpty();
        }

        @Test
        @DisplayName("상한 직전이면 만들 수 있다")
        void 상한_직전이면_만들_수_있다() {
            given(liveRepository.countBySellerIdAndStatus(SELLER_ID, LiveStatus.READY))
                    .willReturn(9);
            givenSaveSucceeds();

            assertThatCode(() -> liveService.create(command())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("방송 중이거나 끝난 라이브는 상한에 세지 않는다")
        void 예정된_것만_센다() {
            givenSaveSucceeds();

            liveService.create(command());

            verify(liveRepository).countBySellerIdAndStatus(SELLER_ID, LiveStatus.READY);
        }

        @Test
        @DisplayName("채널명은 셀러를 식별할 수 있고 IVS 허용 문자와 길이를 지킨다")
        void 채널명은_IVS_제약을_지킨다() {
            givenSaveSucceeds();

            liveService.create(command());

            List<String> names = streamingClient.createdChannelNames();
            assertThat(names).hasSize(1);
            assertThat(names.get(0))
                    .startsWith("toasty-live-" + SELLER_ID + "-")
                    .matches("[a-zA-Z0-9\\-_]{1,128}");
        }

        @Test
        @DisplayName("같은 셀러가 여러 번 만들어도 채널명이 겹치지 않는다")
        void 채널명이_겹치지_않는다() {
            givenSaveSucceeds();

            liveService.create(command());
            liveService.create(command());

            assertThat(streamingClient.createdChannelNames()).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("저장이 실패하면 방금 만든 채널을 삭제해 고아 리소스를 남기지 않는다")
        void 저장_실패시_채널을_보상_삭제한다() {
            RuntimeException saveFailure = new DataIntegrityViolationException("public_id 중복");
            given(liveRepository.save(any(Live.class))).willThrow(saveFailure);

            assertThatThrownBy(() -> liveService.create(command())).isSameAs(saveFailure);

            String createdName = streamingClient.createdChannelNames().get(0);
            assertThat(streamingClient.deletedChannelArns())
                    .containsExactly(FakeLiveStreamingClient.arnOf(createdName));
        }

        @Test
        @DisplayName("보상 삭제까지 실패해도 삭제 예외가 원래 예외를 가리지 않는다")
        void 보상_삭제_실패가_원래_예외를_가리지_않는다() {
            RuntimeException saveFailure = new DataIntegrityViolationException("public_id 중복");
            given(liveRepository.save(any(Live.class))).willThrow(saveFailure);
            streamingClient.failOnDelete(
                    new CustomException(LiveErrorCode.LIVE_CHANNEL_DELETE_FAILED));

            assertThatThrownBy(() -> liveService.create(command())).isSameAs(saveFailure);
        }

        @Test
        @DisplayName("채널 생성이 실패하면 저장을 시도하지 않는다")
        void 채널_생성_실패시_저장하지_않는다() {
            FakeLiveStreamingClient failing =
                    new FakeLiveStreamingClient() {
                        @Override
                        public com.toasty.domain.live.client.dto.StreamingChannel createChannel(
                                String channelName) {
                            throw new CustomException(LiveErrorCode.LIVE_CHANNEL_CREATE_FAILED);
                        }
                    };
            LiveService service =
                    new LiveService(
                            liveRepository, failing, productService, passthroughTransaction());

            assertThatThrownBy(() -> service.create(command()))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_CHANNEL_CREATE_FAILED);

            assertThat(failing.deletedChannelArns()).isEmpty();
        }
    }

    @Nested
    @DisplayName("라이브 시청")
    class GetByPublicId {

        @Test
        @DisplayName("publicId로 조회해 저장된 값을 그대로 반환한다")
        void 저장된_값을_반환한다() {
            givenLiveByPublicId("public-id");

            LiveDetailResponse response = liveService.getByPublicId("public-id");

            assertThat(response.sellerId()).isEqualTo(SELLER_ID);
            assertThat(response.playbackUrl()).isEqualTo("https://playback/abc.m3u8");
            assertThat(response.status()).isEqualTo(LiveStatus.READY);
            assertThat(response.startedAt()).isNull();
            assertThat(response.endedAt()).isNull();
        }

        @Test
        @DisplayName("없는 publicId면 LIVE_NOT_FOUND다")
        void 없으면_LIVE_NOT_FOUND다() {
            given(liveRepository.findByPublicId("unknown")).willReturn(Optional.empty());

            assertThatThrownBy(() -> liveService.getByPublicId("unknown"))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("라이브 상세 조회")
    class GetMyLive {

        @Test
        @DisplayName("라이브와 편성 상품을 노출 순서대로 함께 준다")
        void 라이브와_상품을_함께_준다() {
            givenLive(1L);
            given(productService.findScheduledProducts(1L))
                    .willReturn(
                            java.util.List.of(
                                    liveProduct(31L, "가죽 벨트", 0), liveProduct(32L, "도자기 컵", 1)));

            LiveWithProductsResponse response = liveService.getMyLiveDetail(1L, SELLER_ID);

            assertThat(response.live().title()).isEqualTo("빈티지 여름옷 라이브");
            assertThat(response.products())
                    .extracting(
                            com.toasty.domain.product.controller.dto.response.LiveProductResponse
                                    ::name)
                    .containsExactly("가죽 벨트", "도자기 컵");
        }

        @Test
        @DisplayName("소유자가 아니면 거부하고 상품을 조회하지 않는다")
        void 소유자가_아니면_거부한다() {
            givenLive(1L);

            assertThatThrownBy(() -> liveService.getMyLiveDetail(1L, 99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_FORBIDDEN);

            verify(productService, never()).findScheduledProducts(any());
        }

        @Test
        @DisplayName("없으면 LIVE_NOT_FOUND다")
        void 없으면_LIVE_NOT_FOUND다() {
            given(liveRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> liveService.getMyLiveDetail(1L, SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_FOUND);
        }

        @Test
        @DisplayName("종료된 라이브도 조회된다")
        void 종료된_라이브도_조회된다() {
            Live live = givenLive(1L);
            live.end();

            LiveWithProductsResponse response = liveService.getMyLiveDetail(1L, SELLER_ID);

            assertThat(response.live().status()).isEqualTo(LiveStatus.ENDED);
        }

        @Test
        @DisplayName("편성된 상품이 없으면 빈 목록이다")
        void 상품이_없으면_비어_있다() {
            givenLive(1L);

            assertThat(liveService.getMyLiveDetail(1L, SELLER_ID).products()).isEmpty();
        }

        private com.toasty.domain.product.controller.dto.response.LiveProductResponse liveProduct(
                Long productId, String name, int displayOrder) {
            return new com.toasty.domain.product.controller.dto.response.LiveProductResponse(
                    productId,
                    productId + 10,
                    name,
                    45000,
                    1,
                    "https://cdn.example.com/a.jpg",
                    displayOrder,
                    com.toasty.domain.product.entity.LiveProductStatus.SCHEDULED);
        }
    }

    @Nested
    @DisplayName("셀러 라이브탭 조회")
    class GetMyLiveTab {

        private Live scheduledLive(Long liveId, String title) {
            Live live =
                    Live.create(
                            new LiveCreateCommand(
                                    SELLER_ID,
                                    title,
                                    "설명",
                                    java.time.LocalDateTime.now().plusDays(1),
                                    java.util.List.of()),
                            "arn:aws:ivs:channel/" + liveId,
                            "https://playback/" + liveId + ".m3u8");
            org.springframework.test.util.ReflectionTestUtils.setField(live, "id", liveId);
            return live;
        }

        @Test
        @DisplayName("방송 중이 없으면 broadcasting은 null이고 예정만 담긴다")
        void 방송_중이_없으면_null이다() {
            given(
                            liveRepository.findBySellerIdAndStatusInOrderByScheduledAtAsc(
                                    eq(SELLER_ID), any()))
                    .willReturn(java.util.List.of(scheduledLive(1L, "예정 라이브")));

            SellerLiveTabResponse response = liveService.getMyLiveTab(SELLER_ID);

            assertThat(response.broadcasting()).isNull();
            assertThat(response.scheduled()).hasSize(1);
            assertThat(response.scheduled().get(0).title()).isEqualTo("예정 라이브");
        }

        @Test
        @DisplayName("방송 중인 라이브는 단건으로 빠지고 예정 목록에는 담기지 않는다")
        void 방송_중은_단건으로_뺀다() {
            Live broadcasting = scheduledLive(1L, "방송 중 라이브");
            broadcasting.startBroadcast();
            given(
                            liveRepository.findBySellerIdAndStatusInOrderByScheduledAtAsc(
                                    eq(SELLER_ID), any()))
                    .willReturn(java.util.List.of(broadcasting, scheduledLive(2L, "예정 라이브")));

            SellerLiveTabResponse response = liveService.getMyLiveTab(SELLER_ID);

            assertThat(response.broadcasting().title()).isEqualTo("방송 중 라이브");
            assertThat(response.broadcasting().publicId()).isNotBlank();
            assertThat(response.scheduled())
                    .extracting(SellerLiveTabResponse.Scheduled::title)
                    .containsExactly("예정 라이브");
        }

        @Test
        @DisplayName("편성 상품 수를 라이브별로 맞춰 담고, 편성이 없으면 0이다")
        void 상품_수를_라이브별로_담는다() {
            given(
                            liveRepository.findBySellerIdAndStatusInOrderByScheduledAtAsc(
                                    eq(SELLER_ID), any()))
                    .willReturn(
                            java.util.List.of(scheduledLive(1L, "첫째"), scheduledLive(2L, "둘째")));
            given(productService.countScheduledProducts(java.util.List.of(1L, 2L)))
                    .willReturn(java.util.Map.of(1L, 32));

            SellerLiveTabResponse response = liveService.getMyLiveTab(SELLER_ID);

            assertThat(response.scheduled())
                    .extracting(
                            SellerLiveTabResponse.Scheduled::title,
                            SellerLiveTabResponse.Scheduled::productCount)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("첫째", 32),
                            org.assertj.core.groups.Tuple.tuple("둘째", 0));
        }

        @Test
        @DisplayName("라이브가 하나도 없으면 빈 목록이고 상품 수를 세지 않는다")
        void 라이브가_없으면_비어_있다() {
            given(
                            liveRepository.findBySellerIdAndStatusInOrderByScheduledAtAsc(
                                    eq(SELLER_ID), any()))
                    .willReturn(java.util.List.of());

            SellerLiveTabResponse response = liveService.getMyLiveTab(SELLER_ID);

            assertThat(response.broadcasting()).isNull();
            assertThat(response.scheduled()).isEmpty();
            assertThat(response.latestStat()).isNull();
        }

        @Test
        @DisplayName("종료된 라이브는 조회 대상에서 빠진다")
        void 종료된_라이브는_조회하지_않는다() {
            given(
                            liveRepository.findBySellerIdAndStatusInOrderByScheduledAtAsc(
                                    eq(SELLER_ID), any()))
                    .willReturn(java.util.List.of());

            liveService.getMyLiveTab(SELLER_ID);

            org.mockito.ArgumentCaptor<java.util.Collection<LiveStatus>> captor =
                    org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
            verify(liveRepository)
                    .findBySellerIdAndStatusInOrderByScheduledAtAsc(
                            eq(SELLER_ID), captor.capture());
            assertThat(captor.getValue())
                    .containsExactlyInAnyOrder(LiveStatus.LIVE, LiveStatus.READY);
        }
    }

    @Nested
    @DisplayName("방송 중 상품 관리")
    class LiveProducts {

        @Test
        @DisplayName("전체 상품 시트에 현재 고정 상품과 편성 목록을 함께 준다")
        void 시트를_채운다() {
            givenLive(1L);
            given(productService.findCurrentPinnedProductId(1L)).willReturn(31L);
            given(productService.findScheduledProducts(1L))
                    .willReturn(
                            java.util.List.of(
                                    new com.toasty.domain.product.controller.dto.response
                                            .LiveProductResponse(
                                            31L,
                                            41L,
                                            "가죽 벨트",
                                            45000,
                                            1,
                                            "https://cdn.example.com/a.jpg",
                                            0,
                                            com.toasty.domain.product.entity.LiveProductStatus
                                                    .ACTIVE)));

            LiveProductsResponse response = liveService.getMyLiveProducts(1L, SELLER_ID);

            assertThat(response.currentPinnedProductId()).isEqualTo(31L);
            assertThat(response.products()).hasSize(1);
        }

        @Test
        @DisplayName("소유자가 아니면 시트를 볼 수 없다")
        void 소유자가_아니면_거부한다() {
            givenLive(1L);

            assertThatThrownBy(() -> liveService.getMyLiveProducts(1L, 99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_FORBIDDEN);

            verify(productService, never()).findScheduledProducts(any());
        }

        @Test
        @DisplayName("방송 중일 때만 고정할 수 있다")
        void 방송_전에는_고정할_수_없다() {
            givenLive(1L);

            assertThatThrownBy(() -> liveService.pinProduct(1L, 31L, SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_BROADCASTING);

            verify(productService, never()).pinForLive(any(), any(), any());
        }

        @Test
        @DisplayName("방송 중이면 고정을 상품 쪽에 넘긴다")
        void 방송_중이면_고정한다() {
            Live live = givenLive(1L);
            live.startBroadcast();

            liveService.pinProduct(1L, 31L, SELLER_ID);

            verify(productService).pinForLive(1L, 31L, SELLER_ID);
        }

        @Test
        @DisplayName("소유자가 아니면 고정할 수 없다")
        void 남의_라이브는_고정할_수_없다() {
            Live live = givenLive(1L);
            live.startBroadcast();

            assertThatThrownBy(() -> liveService.pinProduct(1L, 31L, 99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_FORBIDDEN);
        }

        @Test
        @DisplayName("방송 중일 때만 가격·재고를 고칠 수 있다")
        void 방송_전에는_수정할_수_없다() {
            givenLive(1L);

            assertThatThrownBy(
                            () ->
                                    liveService.changeProductPriceAndStock(
                                            1L, 31L, SELLER_ID, 39000, 5))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_BROADCASTING);

            verify(productService, never())
                    .changePriceAndStockDuringLive(any(), any(), any(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("라이브 삭제")
    class Delete {

        @Test
        @DisplayName("소유자가 아니면 거부하고 상품을 건드리지 않는다")
        void 소유자가_아니면_거부한다() {
            givenLive(1L);

            assertThatThrownBy(() -> liveService.delete(1L, 99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_FORBIDDEN);

            verify(productService, never()).removeAllForLive(any());
            verify(liveRepository, never()).delete(any(Live.class));
            assertThat(streamingClient.deletedChannelArns()).isEmpty();
        }

        @Test
        @DisplayName("방송 중인 라이브는 지울 수 없다")
        void 방송_중에는_지울_수_없다() {
            Live live = givenLive(1L);
            live.startBroadcast();

            assertThatThrownBy(() -> liveService.delete(1L, SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_DELETABLE);

            verify(productService, never()).removeAllForLive(any());
        }

        @Test
        @DisplayName("종료된 라이브는 지울 수 없다")
        void 종료된_라이브는_지울_수_없다() {
            Live live = givenLive(1L);
            live.end();

            assertThatThrownBy(() -> liveService.delete(1L, SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_DELETABLE);
        }

        @Test
        @DisplayName("편성 상품과 라이브를 지우고 커밋 뒤에 채널과 사진을 정리한다")
        void 지우고_커밋_뒤에_정리한다() {
            Live live = givenLive(1L);
            given(productService.removeAllForLive(1L))
                    .willReturn(java.util.List.of("products/images/7/a.jpg"));

            liveService.delete(1L, SELLER_ID);

            verify(liveRepository).delete(live);
            verify(productService)
                    .deleteImagesQuietly(java.util.List.of("products/images/7/a.jpg"));
            assertThat(streamingClient.deletedChannelArns())
                    .containsExactly("arn:aws:ivs:channel/abc");
        }

        @Test
        @DisplayName("커밋 뒤 채널 삭제가 실패해도 요청은 성공한다")
        void 채널_삭제_실패는_요청을_실패시키지_않는다() {
            givenLive(1L);
            streamingClient.failOnDelete(
                    new CustomException(LiveErrorCode.LIVE_CHANNEL_DELETE_FAILED));

            assertThatCode(() -> liveService.delete(1L, SELLER_ID)).doesNotThrowAnyException();

            verify(productService).deleteImagesQuietly(java.util.List.of());
        }

        @Test
        @DisplayName("없으면 LIVE_NOT_FOUND다")
        void 없으면_LIVE_NOT_FOUND다() {
            given(liveRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> liveService.delete(1L, SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("송출정보 재발급")
    class ReissueCredential {

        @Test
        @DisplayName("소유자가 아니면 LIVE_FORBIDDEN이다")
        void 소유자가_아니면_거부한다() {
            givenLive(1L);

            assertThatThrownBy(() -> liveService.reissueCredential(1L, 99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_FORBIDDEN);

            assertThat(streamingClient.reissuedChannelArns()).isEmpty();
        }

        @Test
        @DisplayName("종료된 라이브는 재발급할 수 없다")
        void 종료된_라이브는_재발급할_수_없다() {
            Live live = givenLive(1L);
            live.end();

            assertThatThrownBy(() -> liveService.reissueCredential(1L, SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_ALREADY_ENDED);

            assertThat(streamingClient.reissuedChannelArns()).isEmpty();
        }

        @Test
        @DisplayName("재발급할 때마다 이전과 다른 키를 받는다")
        void 재발급하면_새_키를_받는다() {
            givenLive(1L);

            BroadcastCredentialResponse first = liveService.reissueCredential(1L, SELLER_ID);
            BroadcastCredentialResponse second = liveService.reissueCredential(1L, SELLER_ID);

            assertThat(first.streamKey()).isNotEqualTo(second.streamKey());
            assertThat(first.ingestEndpoint()).isEqualTo(FakeLiveStreamingClient.INGEST_ENDPOINT);
            assertThat(streamingClient.reissuedChannelArns()).hasSize(2);
        }

        @Test
        @DisplayName("없는 라이브면 LIVE_NOT_FOUND다")
        void 없으면_LIVE_NOT_FOUND다() {
            given(liveRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> liveService.reissueCredential(99L, SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("송출 상태 조회")
    class GetStreamStatus {

        @Test
        @DisplayName("소유자가 아니면 LIVE_FORBIDDEN이다")
        void 소유자가_아니면_거부한다() {
            givenLive(1L);

            assertThatThrownBy(() -> liveService.getStreamStatus(1L, 99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_FORBIDDEN);
        }

        @Test
        @DisplayName("송출이 확인되면 LIVE로 전이하고 저장한다")
        void 송출이_확인되면_LIVE로_전이한다() {
            givenLive(1L);
            givenSaveSucceeds();
            streamingClient.broadcasting(StreamState.BROADCASTING);

            LiveStreamStatusResponse response = liveService.getStreamStatus(1L, SELLER_ID);

            assertThat(response.status()).isEqualTo(LiveStatus.LIVE);
            assertThat(response.broadcasting()).isTrue();
            assertThat(response.startedAt()).isNotNull();
            verify(liveRepository).save(any(Live.class));
        }

        @Test
        @DisplayName("송출 중이 아니면 상태를 바꾸지 않는다")
        void 송출_중이_아니면_상태를_유지한다() {
            givenLive(1L);
            streamingClient.broadcasting(StreamState.NOT_BROADCASTING);

            LiveStreamStatusResponse response = liveService.getStreamStatus(1L, SELLER_ID);

            assertThat(response.status()).isEqualTo(LiveStatus.READY);
            assertThat(response.broadcasting()).isFalse();
            verify(liveRepository, never()).save(any(Live.class));
        }

        @Test
        @DisplayName("이미 LIVE면 다시 저장하지 않는다")
        void 이미_LIVE면_저장하지_않는다() {
            Live live = givenLive(1L);
            live.startBroadcast();
            streamingClient.broadcasting(StreamState.BROADCASTING);

            LiveStreamStatusResponse response = liveService.getStreamStatus(1L, SELLER_ID);

            assertThat(response.status()).isEqualTo(LiveStatus.LIVE);
            verify(liveRepository, never()).save(any(Live.class));
        }

        @Test
        @DisplayName("종료된 라이브는 동기화하지 않는다")
        void 종료된_라이브는_동기화하지_않는다() {
            Live live = givenLive(1L);
            live.end();
            streamingClient.broadcasting(StreamState.BROADCASTING);

            LiveStreamStatusResponse response = liveService.getStreamStatus(1L, SELLER_ID);

            assertThat(response.status()).isEqualTo(LiveStatus.ENDED);
            verify(liveRepository, never()).save(any(Live.class));
        }

        @Test
        @DisplayName("이미 다른 라이브를 방송 중인 셀러면 LIVE_ALREADY_BROADCASTING이다")
        void 동시_방송은_거부한다() {
            givenLive(1L);
            given(liveRepository.save(any(Live.class)))
                    .willThrow(new DataIntegrityViolationException("uk_lives_active_seller_id"));
            streamingClient.broadcasting(StreamState.BROADCASTING);

            assertThatThrownBy(() -> liveService.getStreamStatus(1L, SELLER_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_ALREADY_BROADCASTING);
        }
    }

    @Nested
    @DisplayName("방송 종료")
    class End {

        @Test
        @DisplayName("소유자가 아니면 LIVE_FORBIDDEN이다")
        void 소유자가_아니면_거부한다() {
            givenLive(1L);

            assertThatThrownBy(() -> liveService.end(1L, 99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_FORBIDDEN);

            assertThat(streamingClient.stoppedChannelArns()).isEmpty();
        }

        @Test
        @DisplayName("송출을 중단하고 스트림 키를 지운 뒤 ENDED가 된다")
        void 송출을_중단하고_키를_지운다() {
            Live live = givenLive(1L);
            live.startBroadcast();
            givenSaveSucceeds();

            LiveDetailResponse response = liveService.end(1L, SELLER_ID);

            assertThat(response.status()).isEqualTo(LiveStatus.ENDED);
            assertThat(response.endedAt()).isNotNull();
            assertThat(response.playbackUrl()).isEqualTo("https://playback/abc.m3u8");
            assertThat(streamingClient.stoppedChannelArns())
                    .containsExactly(live.getIvsChannelArn());
            assertThat(streamingClient.streamKeyDeletedChannelArns())
                    .containsExactly(live.getIvsChannelArn());
        }

        @Test
        @DisplayName("이미 종료됐으면 IVS를 호출하지 않는다")
        void 이미_종료됐으면_IVS를_부르지_않는다() {
            Live live = givenLive(1L);
            live.end();

            LiveDetailResponse response = liveService.end(1L, SELLER_ID);

            assertThat(response.status()).isEqualTo(LiveStatus.ENDED);
            assertThat(streamingClient.stoppedChannelArns()).isEmpty();
            assertThat(streamingClient.streamKeyDeletedChannelArns()).isEmpty();
            verify(liveRepository, never()).save(any(Live.class));
        }
    }

    @Nested
    @DisplayName("재생 정보 조회")
    class GetPlayback {

        @Test
        @DisplayName("재생 URL과 상태만 반환한다")
        void 재생_URL과_상태를_반환한다() {
            givenLiveByPublicId("public-id");

            LivePlaybackResponse response = liveService.getPlayback("public-id");

            assertThat(response.playbackUrl()).isEqualTo("https://playback/abc.m3u8");
            assertThat(response.status()).isEqualTo(LiveStatus.READY);
        }
    }

    private static LiveUpdateCommand updateCommand(
            String title,
            java.util.List<com.toasty.domain.product.entity.ProductUpsertCommand> products) {
        return new LiveUpdateCommand(1L, SELLER_ID, title, null, null, products);
    }

    @Nested
    @DisplayName("라이브 수정")
    class Update {

        @Test
        @DisplayName("보낸 필드만 바꾸고 나머지는 그대로 둔다")
        void 보낸_필드만_바꾼다() {
            givenLive(1L);

            LiveDetailResponse response = liveService.update(updateCommand("바뀐 제목", null));

            assertThat(response.title()).isEqualTo("바뀐 제목");
            assertThat(response.description()).isEqualTo("여름 상품을 소개합니다");
        }

        @Test
        @DisplayName("products를 보내지 않으면 상품을 건드리지 않는다")
        void 상품을_보내지_않으면_그대로_둔다() {
            givenLive(1L);

            liveService.update(updateCommand("바뀐 제목", null));

            verify(productService, never()).copyNewImagesToPermanent(any(), any());
            verify(productService, never()).replaceForLive(any(), any(), any(), any());
        }

        @Test
        @DisplayName("소유자가 아니면 거부한다")
        void 소유자가_아니면_거부한다() {
            givenLive(1L);

            assertThatThrownBy(
                            () ->
                                    liveService.update(
                                            new LiveUpdateCommand(
                                                    1L, 99L, "바뀐 제목", null, null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_FORBIDDEN);
        }

        @Test
        @DisplayName("방송이 시작된 라이브는 수정할 수 없고 사진도 복사하지 않는다")
        void 방송_중에는_수정할_수_없다() {
            Live live = givenLive(1L);
            live.startBroadcast();

            assertThatThrownBy(() -> liveService.update(updateCommand("바뀐 제목", null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_EDITABLE);
            verify(productService, never()).copyNewImagesToPermanent(any(), any());
        }

        @Test
        @DisplayName("종료된 라이브는 수정할 수 없다")
        void 종료된_라이브는_수정할_수_없다() {
            Live live = givenLive(1L);
            live.end();

            assertThatThrownBy(() -> liveService.update(updateCommand("바뀐 제목", null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_EDITABLE);
        }

        @Test
        @DisplayName("편성에서 빠진 사진만 트랜잭션이 끝난 뒤에 지우고, 이번에 저장한 사진은 건드리지 않는다")
        void 빠진_사진을_커밋_뒤에_지운다() {
            givenLive(1L);
            given(productService.copyNewImagesToPermanent(any(), any()))
                    .willReturn(java.util.List.of("products/images/7/new.jpg"));
            given(productService.replaceForLive(any(), any(), any(), any()))
                    .willReturn(java.util.List.of("products/images/7/old.jpg"));

            liveService.update(updateCommand("바뀐 제목", java.util.List.of(upsert())));

            verify(productService)
                    .deleteImagesQuietly(java.util.List.of("products/images/7/old.jpg"));
            verify(productService, never())
                    .deleteImagesQuietly(java.util.List.of("products/images/7/new.jpg"));
        }

        @Test
        @DisplayName("저장이 실패하면 이번에 복사한 사진을 되돌린다")
        void 실패하면_복사한_사진을_지운다() {
            givenLive(1L);
            given(productService.copyNewImagesToPermanent(any(), any()))
                    .willReturn(java.util.List.of("products/images/7/new.jpg"));
            given(productService.replaceForLive(any(), any(), any(), any()))
                    .willThrow(new IllegalStateException("저장 실패"));

            assertThatThrownBy(
                            () ->
                                    liveService.update(
                                            updateCommand("바뀐 제목", java.util.List.of(upsert()))))
                    .isInstanceOf(IllegalStateException.class);

            verify(productService)
                    .deleteImagesQuietly(java.util.List.of("products/images/7/new.jpg"));
        }

        @Test
        @DisplayName("사진을 복사하는 동안 송출이 시작되면 거부한다")
        void 복사_도중_송출이_시작되면_거부한다() {
            Live live = givenLive(1L);
            given(productService.copyNewImagesToPermanent(any(), any()))
                    .willAnswer(
                            call -> {
                                live.startBroadcast();
                                return java.util.List.of("products/images/7/new.jpg");
                            });

            assertThatThrownBy(
                            () ->
                                    liveService.update(
                                            updateCommand("바뀐 제목", java.util.List.of(upsert()))))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_EDITABLE);

            verify(productService, never()).replaceForLive(any(), any(), any(), any());
            verify(productService)
                    .deleteImagesQuietly(java.util.List.of("products/images/7/new.jpg"));
        }

        private com.toasty.domain.product.entity.ProductUpsertCommand upsert() {
            return new com.toasty.domain.product.entity.ProductUpsertCommand(
                    null, "도자기 컵", 12000, 3, null, "products/pending/7/b.jpg");
        }
    }
}
