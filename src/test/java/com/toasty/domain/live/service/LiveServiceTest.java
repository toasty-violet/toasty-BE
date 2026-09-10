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

import com.toasty.domain.auth.entity.AuthUser;
import com.toasty.domain.live.client.FakeLiveChatClient;
import com.toasty.domain.live.client.FakeLiveStreamingClient;
import com.toasty.domain.live.client.dto.StreamState;
import com.toasty.domain.live.controller.dto.response.BroadcastCredentialResponse;
import com.toasty.domain.live.controller.dto.response.LiveChatTokenResponse;
import com.toasty.domain.live.controller.dto.response.LiveDetailResponse;
import com.toasty.domain.live.controller.dto.response.LivePlaybackResponse;
import com.toasty.domain.live.controller.dto.response.LiveStreamStatusResponse;
import com.toasty.domain.live.controller.dto.response.LiveViewerResponse;
import com.toasty.domain.live.controller.dto.response.LiveWithProductsResponse;
import com.toasty.domain.live.controller.dto.response.SellerLiveTabResponse;
import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveCreateCommand;
import com.toasty.domain.live.entity.LiveStatus;
import com.toasty.domain.live.entity.LiveUpdateCommand;
import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.domain.live.repository.LiveRepository;
import com.toasty.domain.product.controller.dto.response.LiveProductResponse;
import com.toasty.domain.product.controller.dto.response.LiveProductsResponse;
import com.toasty.domain.product.entity.LiveProductStatus;
import com.toasty.domain.seller.controller.dto.response.SellerProfileResponse;
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
    private static final String CHAT_ROOM_ARN = "arn:aws:ivschat:room/abc";

    private LiveRepository liveRepository;
    private FakeLiveStreamingClient streamingClient;
    private FakeLiveChatClient chatClient;
    private com.toasty.domain.product.service.ProductService productService;
    private com.toasty.domain.user.service.UserService userService;
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private LiveService liveService;

    @BeforeEach
    void setUp() {
        liveRepository = mock(LiveRepository.class);
        streamingClient = new FakeLiveStreamingClient();
        chatClient = new FakeLiveChatClient();
        productService = mock(com.toasty.domain.product.service.ProductService.class);
        userService = mock(com.toasty.domain.user.service.UserService.class);
        transactionTemplate = passthroughTransaction();
        liveService =
                new LiveService(
                        liveRepository,
                        streamingClient,
                        chatClient,
                        productService,
                        userService,
                        transactionTemplate);
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
        Live live =
                Live.create(
                        command(),
                        "arn:aws:ivs:channel/abc",
                        "https://playback/abc.m3u8",
                        CHAT_ROOM_ARN);
        given(liveRepository.findById(liveId)).willReturn(Optional.of(live));
        return live;
    }

    private Live givenLiveByPublicId(String publicId) {
        Live live =
                Live.create(
                        command(),
                        "arn:aws:ivs:channel/abc",
                        "https://playback/abc.m3u8",
                        CHAT_ROOM_ARN);
        given(liveRepository.findByPublicId(publicId)).willReturn(Optional.of(live));
        return live;
    }

    private void givenLiveByPublicId(String publicId, Long liveId) {
        org.springframework.test.util.ReflectionTestUtils.setField(
                givenLiveByPublicId(publicId), "id", liveId);
    }

    private LiveProductResponse liveProduct(
            Long productId, String name, int displayOrder, LiveProductStatus status) {
        return new LiveProductResponse(
                productId,
                productId + 10,
                name,
                45000,
                1,
                "https://cdn.example.com/a.jpg",
                displayOrder,
                status);
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
        @DisplayName("채널과 같은 이름으로 채팅방도 함께 만든다")
        void 채팅방도_함께_만든다() {
            givenSaveSucceeds();

            liveService.create(command());

            assertThat(chatClient.createdRoomNames()).hasSize(1);
            assertThat(chatClient.createdRoomNames().get(0))
                    .isEqualTo(streamingClient.createdChannelNames().get(0));
        }

        @Test
        @DisplayName("채팅방 생성이 실패하면 이미 만든 채널과 복사한 사진을 지운다")
        void 채팅방_생성_실패시_채널과_사진을_지운다() {
            List<String> imageKeys = List.of("products/images/44/a.jpg");
            given(productService.copyImagesToPermanent(any(), any())).willReturn(imageKeys);
            chatClient.failOnCreate(
                    new CustomException(LiveErrorCode.LIVE_CHAT_ROOM_CREATE_FAILED));

            assertThatThrownBy(() -> liveService.create(command()))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_CHAT_ROOM_CREATE_FAILED);

            assertThat(streamingClient.deletedChannelArns()).hasSize(1);
            verify(productService).deleteImagesQuietly(imageKeys);
            verify(liveRepository, never()).save(any(Live.class));
        }

        @Test
        @DisplayName("저장이 실패하면 방금 만든 채팅방도 함께 지운다")
        void 저장_실패시_채팅방도_지운다() {
            given(liveRepository.save(any(Live.class)))
                    .willThrow(new CustomException(LiveErrorCode.LIVE_NOT_FOUND));

            assertThatThrownBy(() -> liveService.create(command()))
                    .isInstanceOf(CustomException.class);

            assertThat(chatClient.deletedRoomArns()).hasSize(1);
            assertThat(streamingClient.deletedChannelArns()).hasSize(1);
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
                            liveRepository,
                            failing,
                            chatClient,
                            productService,
                            userService,
                            passthroughTransaction());

            assertThatThrownBy(() -> service.create(command()))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_CHANNEL_CREATE_FAILED);

            assertThat(failing.deletedChannelArns()).isEmpty();
        }
    }

    @Nested
    @DisplayName("시청자 수 조회")
    class ViewerCount {

        @Test
        @DisplayName("배치가 적어둔 값을 읽고 IVS를 부르지 않는다")
        void 적어둔_값을_읽는다() {
            Live live = givenLiveByPublicId("abc");
            live.updateViewerCount(132);

            assertThat(liveService.getViewerCount("abc").viewerCount()).isEqualTo(132);

            assertThat(streamingClient.streamStatusRequestCount()).isZero();
        }

        @Test
        @DisplayName("없는 publicId면 LIVE_NOT_FOUND다")
        void 없으면_LIVE_NOT_FOUND다() {
            given(liveRepository.findByPublicId("unknown")).willReturn(Optional.empty());

            assertThatThrownBy(() -> liveService.getViewerCount("unknown"))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("방송 시작 시 시청자 수")
    class ViewerCountOnStart {

        @Test
        @DisplayName("방송 시작을 기록할 때 그때 받은 시청자 수도 함께 적는다")
        void 시작할_때_함께_적는다() {
            Live live = givenLive(1L);
            streamingClient.broadcasting(StreamState.BROADCASTING);
            streamingClient.viewerCount(132);
            givenSaveSucceeds();

            liveService.getStreamStatus(1L, SELLER_ID);

            assertThat(live.getViewerCount()).isEqualTo(132);
        }
    }

    @Nested
    @DisplayName("시청자 수 갱신")
    class RefreshViewerCounts {

        private Live live(Long liveId) {
            Live live =
                    Live.create(
                            command(),
                            "arn:aws:ivs:channel/" + liveId,
                            "https://playback/abc.m3u8",
                            CHAT_ROOM_ARN);
            org.springframework.test.util.ReflectionTestUtils.setField(live, "id", liveId);
            return live;
        }

        private void givenUnfinished(Live... lives) {
            given(liveRepository.findByStatusIn(any())).willReturn(java.util.List.of(lives));
        }

        @Test
        @DisplayName("방송 중인 라이브의 시청자 수가 바뀌면 적어둔다")
        void 바뀌면_적어둔다() {
            Live live = live(1L);
            live.startBroadcast();
            givenUnfinished(live);
            streamingClient.broadcasting(StreamState.BROADCASTING);
            streamingClient.viewerCount(132);

            liveService.refreshViewerCounts();

            verify(liveRepository).updateViewerCount(1L, 132);
        }

        @Test
        @DisplayName("시청자 수가 그대로면 쓰지 않는다")
        void 그대로면_쓰지_않는다() {
            Live live = live(1L);
            live.startBroadcast();
            live.updateViewerCount(132);
            givenUnfinished(live);
            streamingClient.broadcasting(StreamState.BROADCASTING);
            streamingClient.viewerCount(132);

            liveService.refreshViewerCounts();

            verify(liveRepository, never()).updateViewerCount(any(), anyInt());
        }

        @Test
        @DisplayName("셀러가 화면을 열지 않아도 송출이 시작되면 방송 중으로 올린다")
        void 송출이_시작되면_상태를_올린다() {
            Live scheduled = live(1L);
            givenUnfinished(scheduled);
            given(liveRepository.findAllById(java.util.List.of(1L)))
                    .willReturn(java.util.List.of(scheduled));
            streamingClient.broadcasting(StreamState.BROADCASTING);
            streamingClient.viewerCount(132);

            liveService.refreshViewerCounts();

            assertThat(scheduled.getStatus()).isEqualTo(LiveStatus.LIVE);
            assertThat(scheduled.getViewerCount()).isEqualTo(132);
            verify(liveRepository, never()).updateViewerCount(any(), anyInt());
        }

        @Test
        @DisplayName("아직 켜지지 않은 예정은 건드리지 않는다")
        void 켜지지_않았으면_건드리지_않는다() {
            givenUnfinished(live(1L));
            streamingClient.broadcasting(StreamState.NOT_BROADCASTING);

            liveService.refreshViewerCounts();

            verify(liveRepository, never()).updateViewerCount(any(), anyInt());
            verify(liveRepository, never()).findAllById(any());
        }

        @Test
        @DisplayName("한 라이브가 실패해도 요청은 성공한다")
        void 실패해도_넘어간다() {
            Live live = live(1L);
            live.startBroadcast();
            givenUnfinished(live);
            streamingClient.failOnStreamStatus(
                    new CustomException(LiveErrorCode.LIVE_STREAM_STATUS_FETCH_FAILED));

            assertThatCode(() -> liveService.refreshViewerCounts()).doesNotThrowAnyException();

            verify(liveRepository, never()).updateViewerCount(any(), anyInt());
        }
    }

    @Nested
    @DisplayName("라이브 시청")
    class GetByPublicId {

        @Test
        @DisplayName("publicId로 조회해 저장된 값과 셀러 정보를 함께 반환한다")
        void 저장된_값을_반환한다() {
            givenLiveByPublicId("public-id");
            given(userService.findSellerProfile(SELLER_ID))
                    .willReturn(
                            new SellerProfileResponse(
                                    SELLER_ID, "토스티샵", "https://cdn.example.com/shop.jpg"));

            LiveViewerResponse response = liveService.getByPublicId("public-id");

            assertThat(response.playbackUrl()).isEqualTo("https://playback/abc.m3u8");
            assertThat(response.status()).isEqualTo(LiveStatus.READY);
            assertThat(response.startedAt()).isNull();
            assertThat(response.endedAt()).isNull();
            assertThat(response.seller().sellerId()).isEqualTo(SELLER_ID);
            assertThat(response.seller().shopName()).isEqualTo("토스티샵");
            assertThat(response.seller().shopImageUrl())
                    .isEqualTo("https://cdn.example.com/shop.jpg");
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
                                    liveProduct(31L, "가죽 벨트", 0, LiveProductStatus.SCHEDULED),
                                    liveProduct(32L, "도자기 컵", 1, LiveProductStatus.SCHEDULED)));

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
                            "https://playback/" + liveId + ".m3u8",
                            CHAT_ROOM_ARN);
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
    @DisplayName("시청 화면 상품 조회")
    class PublicLiveProducts {

        @Test
        @DisplayName("publicId로 현재 고정 상품과 편성 목록을 함께 준다")
        void 시트를_채운다() {
            givenLiveByPublicId("abc", 1L);
            LiveProductsResponse sheet =
                    new LiveProductsResponse(
                            31L,
                            java.util.List.of(
                                    liveProduct(31L, "가죽 벨트", 0, LiveProductStatus.ACTIVE)));
            given(productService.findLiveProducts(1L)).willReturn(sheet);

            assertThat(liveService.getPublicLiveProducts("abc")).isSameAs(sheet);
        }

        @Test
        @DisplayName("없는 publicId면 LIVE_NOT_FOUND다")
        void 없으면_LIVE_NOT_FOUND다() {
            given(liveRepository.findByPublicId("unknown")).willReturn(Optional.empty());

            assertThatThrownBy(() -> liveService.getPublicLiveProducts("unknown"))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_FOUND);

            verify(productService, never()).findLiveProducts(any());
        }
    }

    @Nested
    @DisplayName("방송 중 상품 관리")
    class LiveProducts {

        @Test
        @DisplayName("전체 상품 시트를 상품 쪽에서 받아 그대로 준다")
        void 시트를_채운다() {
            givenLive(1L);
            LiveProductsResponse sheet = new LiveProductsResponse(31L, java.util.List.of());
            given(productService.findLiveProducts(1L)).willReturn(sheet);

            assertThat(liveService.getMyLiveProducts(1L, SELLER_ID)).isSameAs(sheet);
        }

        @Test
        @DisplayName("소유자가 아니면 시트를 볼 수 없다")
        void 소유자가_아니면_거부한다() {
            givenLive(1L);

            assertThatThrownBy(() -> liveService.getMyLiveProducts(1L, 99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_FORBIDDEN);

            verify(productService, never()).findLiveProducts(any());
        }

        @Test
        @DisplayName("방송 중일 때만 고정할 수 있다")
        void 방송_전에는_고정할_수_없다() {
            givenLive(1L);

            assertThatThrownBy(
                            () ->
                                    liveService.pinProduct(
                                            new com.toasty.domain.product.entity
                                                    .LiveProductPinCommand(1L, 31L, SELLER_ID)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_BROADCASTING);

            verify(productService, never()).pinForLive(any());
        }

        @Test
        @DisplayName("방송 중이면 고정을 상품 쪽에 넘긴다")
        void 방송_중이면_고정한다() {
            Live live = givenLive(1L);
            live.startBroadcast();

            liveService.pinProduct(
                    new com.toasty.domain.product.entity.LiveProductPinCommand(1L, 31L, SELLER_ID));

            verify(productService)
                    .pinForLive(
                            new com.toasty.domain.product.entity.LiveProductPinCommand(
                                    1L, 31L, SELLER_ID));
        }

        @Test
        @DisplayName("소유자가 아니면 고정할 수 없다")
        void 남의_라이브는_고정할_수_없다() {
            Live live = givenLive(1L);
            live.startBroadcast();

            assertThatThrownBy(
                            () ->
                                    liveService.pinProduct(
                                            new com.toasty.domain.product.entity
                                                    .LiveProductPinCommand(1L, 31L, 99L)))
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
                                            new com.toasty.domain.product.entity
                                                    .LiveProductUpdateCommand(
                                                    1L, 31L, SELLER_ID, 39000, 5)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_BROADCASTING);

            verify(productService, never()).changePriceAndStockDuringLive(any());
        }
    }

    @Nested
    @DisplayName("채팅 입장 토큰 발급")
    class IssueChatToken {

        @Test
        @DisplayName("비로그인 시청자는 읽기 전용으로 받는다")
        void 비로그인은_읽기_전용이다() {
            givenLiveByPublicId("abc");

            LiveChatTokenResponse response = liveService.issueChatToken("abc", null);

            assertThat(response.writable()).isFalse();
            com.toasty.domain.live.client.dto.ChatTokenCommand issued =
                    chatClient.issuedTokenCommands().get(0);
            assertThat(issued.writable()).isFalse();
            assertThat(issued.role()).isEqualTo(com.toasty.domain.live.client.dto.ChatRole.GUEST);
            assertThat(issued.chatUserId()).startsWith("guest-");
            assertThat(issued.displayName()).isNull();
        }

        @Test
        @DisplayName("로그인 구매자는 쓰기 권한과 닉네임을 받는다")
        void 구매자는_쓸_수_있다() {
            givenLiveByPublicId("abc");
            given(userService.findNickname(9L)).willReturn("토스티러버");

            LiveChatTokenResponse response =
                    liveService.issueChatToken(
                            "abc",
                            new AuthUser(
                                    9L, com.toasty.domain.user.entity.Role.CUSTOMER, 3L, null));

            assertThat(response.writable()).isTrue();
            com.toasty.domain.live.client.dto.ChatTokenCommand issued =
                    chatClient.issuedTokenCommands().get(0);
            assertThat(issued.role())
                    .isEqualTo(com.toasty.domain.live.client.dto.ChatRole.CUSTOMER);
            assertThat(issued.chatUserId()).isEqualTo("user-9");
            assertThat(issued.displayName()).isEqualTo("토스티러버");
        }

        @Test
        @DisplayName("방송을 진행하는 셀러는 SELLER로 받는다")
        void 셀러는_SELLER다() {
            givenLiveByPublicId("abc");
            given(userService.findNickname(9L)).willReturn("토스티샵");

            liveService.issueChatToken(
                    "abc",
                    new AuthUser(9L, com.toasty.domain.user.entity.Role.SELLER, null, SELLER_ID));

            assertThat(chatClient.issuedTokenCommands().get(0).role())
                    .isEqualTo(com.toasty.domain.live.client.dto.ChatRole.SELLER);
        }

        @Test
        @DisplayName("다른 셀러가 보면 CUSTOMER로 받는다")
        void 남의_방송을_보는_셀러는_CUSTOMER다() {
            givenLiveByPublicId("abc");
            given(userService.findNickname(9L)).willReturn("다른샵");

            liveService.issueChatToken(
                    "abc", new AuthUser(9L, com.toasty.domain.user.entity.Role.SELLER, null, 99L));

            assertThat(chatClient.issuedTokenCommands().get(0).role())
                    .isEqualTo(com.toasty.domain.live.client.dto.ChatRole.CUSTOMER);
        }

        @Test
        @DisplayName("끝난 방송은 로그인해도 읽기 전용이다")
        void 끝난_방송은_읽기_전용이다() {
            Live live = givenLiveByPublicId("abc");
            live.end();
            given(userService.findNickname(9L)).willReturn("토스티러버");

            LiveChatTokenResponse response =
                    liveService.issueChatToken(
                            "abc",
                            new AuthUser(
                                    9L, com.toasty.domain.user.entity.Role.CUSTOMER, 3L, null));

            assertThat(response.writable()).isFalse();
            assertThat(chatClient.issuedTokenCommands().get(0).writable()).isFalse();
        }

        @Test
        @DisplayName("토큰 발급이 일시적으로 실패하면 다시 시도하도록 알린다")
        void 일시_실패는_그대로_올라온다() {
            givenLiveByPublicId("abc");
            chatClient.failOnCreateToken(
                    new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE));

            assertThatThrownBy(() -> liveService.issueChatToken("abc", null))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE);
        }

        @Test
        @DisplayName("채팅방이 없는 라이브면 LIVE_CHAT_ROOM_NOT_FOUND다")
        void 방이_없으면_거부한다() {
            Live live = givenLiveByPublicId("abc");
            org.springframework.test.util.ReflectionTestUtils.setField(
                    live, "ivsChatRoomArn", null);

            assertThatThrownBy(() -> liveService.issueChatToken("abc", null))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_CHAT_ROOM_NOT_FOUND);

            assertThat(chatClient.issuedTokenCommands()).isEmpty();
        }
    }

    @Nested
    @DisplayName("종료된 라이브의 채팅방 회수")
    class CleanUpEndedChatRooms {

        @Test
        @DisplayName("방을 지우고 라이브의 방 자리를 비운다")
        void 방을_지우고_자리를_비운다() {
            Live live = givenEndedLive();
            givenSaveSucceeds();

            liveService.cleanUpEndedChatRooms();

            assertThat(chatClient.deletedRoomArns()).containsExactly(CHAT_ROOM_ARN);
            assertThat(live.getIvsChatRoomArn()).isNull();
            verify(liveRepository).save(live);
        }

        @Test
        @DisplayName("삭제가 실패하면 방 자리를 그대로 둬 다음 차례에 다시 시도한다")
        void 실패하면_자리를_남긴다() {
            Live live = givenEndedLive();
            chatClient.failOnDelete(
                    new CustomException(LiveErrorCode.LIVE_CHAT_ROOM_DELETE_FAILED));

            assertThatCode(() -> liveService.cleanUpEndedChatRooms()).doesNotThrowAnyException();

            assertThat(live.getIvsChatRoomArn()).isEqualTo(CHAT_ROOM_ARN);
            verify(liveRepository, never()).save(any(Live.class));
        }

        private Live givenEndedLive() {
            Live live =
                    Live.create(
                            command(),
                            "arn:aws:ivs:channel/abc",
                            "https://playback/abc.m3u8",
                            CHAT_ROOM_ARN);
            live.end();
            given(
                            liveRepository.findByStatusAndEndedAtBeforeAndIvsChatRoomArnIsNotNull(
                                    any(), any()))
                    .willReturn(java.util.List.of(live));
            return live;
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
        @DisplayName("라이브를 지우면 채팅방도 지운다")
        void 채팅방도_지운다() {
            givenLive(1L);

            liveService.delete(1L, SELLER_ID);

            assertThat(chatClient.deletedRoomArns()).containsExactly(CHAT_ROOM_ARN);
        }

        @Test
        @DisplayName("커밋 뒤 채팅방 삭제가 실패해도 요청은 성공한다")
        void 채팅방_삭제_실패는_요청을_실패시키지_않는다() {
            givenLive(1L);
            chatClient.failOnDelete(
                    new CustomException(LiveErrorCode.LIVE_CHAT_ROOM_DELETE_FAILED));

            assertThatCode(() -> liveService.delete(1L, SELLER_ID)).doesNotThrowAnyException();

            assertThat(streamingClient.deletedChannelArns()).hasSize(1);
        }

        @Test
        @DisplayName("채팅방이 없던 라이브는 삭제를 시도하지 않는다")
        void 채팅방이_없으면_건너뛴다() {
            Live live = givenLive(1L);
            org.springframework.test.util.ReflectionTestUtils.setField(
                    live, "ivsChatRoomArn", null);

            liveService.delete(1L, SELLER_ID);

            assertThat(chatClient.deletedRoomArns()).isEmpty();
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
