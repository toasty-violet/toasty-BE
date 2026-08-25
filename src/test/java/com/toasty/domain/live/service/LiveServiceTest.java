package com.toasty.domain.live.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.toasty.domain.live.client.FakeLiveStreamingClient;
import com.toasty.domain.live.controller.dto.response.LiveCreateResponse;
import com.toasty.domain.live.controller.dto.response.LiveDetailResponse;
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
    private LiveService liveService;

    @BeforeEach
    void setUp() {
        liveRepository = mock(LiveRepository.class);
        streamingClient = new FakeLiveStreamingClient();
        liveService = new LiveService(liveRepository, streamingClient);
    }

    private static LiveCreateCommand command() {
        return new LiveCreateCommand(SELLER_ID, "빈티지 여름옷 라이브", "여름 상품을 소개합니다");
    }

    private void givenSaveSucceeds() {
        given(liveRepository.save(any(Live.class))).willAnswer(call -> call.getArgument(0));
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
            assertThat(response.broadcastCredential().streamKey())
                    .isEqualTo(FakeLiveStreamingClient.STREAM_KEY);
            assertThat(response.broadcastCredential().ingestEndpoint())
                    .isEqualTo(FakeLiveStreamingClient.INGEST_ENDPOINT);
            assertThat(streamingClient.deletedChannelArns()).isEmpty();
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
            LiveService service = new LiveService(liveRepository, failing);

            assertThatThrownBy(() -> service.create(command()))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_CHANNEL_CREATE_FAILED);

            assertThat(failing.deletedChannelArns()).isEmpty();
        }
    }

    @Nested
    @DisplayName("라이브 상세 조회")
    class GetById {

        @Test
        @DisplayName("저장된 값을 그대로 반환한다")
        void 저장된_값을_반환한다() {
            Live live =
                    Live.create(command(), "arn:aws:ivs:channel/abc", "https://playback/abc.m3u8");
            given(liveRepository.findById(1L)).willReturn(Optional.of(live));

            LiveDetailResponse response = liveService.getById(1L);

            assertThat(response.sellerId()).isEqualTo(SELLER_ID);
            assertThat(response.playbackUrl()).isEqualTo("https://playback/abc.m3u8");
            assertThat(response.status()).isEqualTo(LiveStatus.READY);
            assertThat(response.startedAt()).isNull();
            assertThat(response.endedAt()).isNull();
        }

        @Test
        @DisplayName("없는 라이브면 LIVE_NOT_FOUND다")
        void 없으면_LIVE_NOT_FOUND다() {
            given(liveRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> liveService.getById(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(LiveErrorCode.LIVE_NOT_FOUND);
        }
    }
}
