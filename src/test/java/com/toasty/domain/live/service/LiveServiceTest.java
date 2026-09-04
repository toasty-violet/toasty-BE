package com.toasty.domain.live.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.toasty.domain.live.client.FakeLiveStreamingClient;
import com.toasty.domain.live.client.dto.StreamState;
import com.toasty.domain.live.controller.dto.response.BroadcastCredentialResponse;
import com.toasty.domain.live.controller.dto.response.LiveCreateResponse;
import com.toasty.domain.live.controller.dto.response.LiveDetailResponse;
import com.toasty.domain.live.controller.dto.response.LivePlaybackResponse;
import com.toasty.domain.live.controller.dto.response.LiveStreamStatusResponse;
import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveCreateCommand;
import com.toasty.domain.live.entity.LiveStatus;
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

            LiveCreateResponse response = liveService.create(command());

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

            LiveCreateResponse response = liveService.create(command());

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
}
