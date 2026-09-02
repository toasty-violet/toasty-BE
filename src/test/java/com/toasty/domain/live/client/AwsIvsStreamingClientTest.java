package com.toasty.domain.live.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.toasty.domain.live.client.dto.BroadcastCredential;
import com.toasty.domain.live.client.dto.StreamState;
import com.toasty.domain.live.client.dto.StreamingChannel;
import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.global.config.IvsProperties;
import com.toasty.global.exception.CustomException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.RetryableException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ivs.IvsClient;
import software.amazon.awssdk.services.ivs.model.AccessDeniedException;
import software.amazon.awssdk.services.ivs.model.Channel;
import software.amazon.awssdk.services.ivs.model.ChannelNotBroadcastingException;
import software.amazon.awssdk.services.ivs.model.CreateChannelResponse;
import software.amazon.awssdk.services.ivs.model.CreateStreamKeyResponse;
import software.amazon.awssdk.services.ivs.model.GetChannelResponse;
import software.amazon.awssdk.services.ivs.model.GetStreamResponse;
import software.amazon.awssdk.services.ivs.model.InternalServerException;
import software.amazon.awssdk.services.ivs.model.ListStreamKeysResponse;
import software.amazon.awssdk.services.ivs.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ivs.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.ivs.model.ServiceUnavailableException;
import software.amazon.awssdk.services.ivs.model.StopStreamResponse;
import software.amazon.awssdk.services.ivs.model.StreamKey;
import software.amazon.awssdk.services.ivs.model.StreamKeySummary;
import software.amazon.awssdk.services.ivs.model.ThrottlingException;
import software.amazon.awssdk.services.ivs.model.ValidationException;

/**
 * isTransient() 분기 검증이 핵심이다. 여기서 넣는 예외는 실제 AWS가 던지는 것이 아니라 우리가 가정한 것이므로, 가정 자체가 맞는지는 실제 IVS 호출로 따로
 * 확인해야 한다.
 */
class AwsIvsStreamingClientTest {

    private static final String CHANNEL_NAME = "toasty-live-7-a1b2c3d4";
    private static final String CHANNEL_ARN = "arn:aws:ivs:ap-northeast-2:1234:channel/abc";

    private IvsClient ivsClient;
    private AwsIvsStreamingClient client;

    @BeforeEach
    void setUp() {
        ivsClient = mock(IvsClient.class);
        client =
                new AwsIvsStreamingClient(
                        ivsClient, new IvsProperties("ap-northeast-2", "BASIC", "LOW"));
    }

    /** 잠시 후 재시도하면 풀릴 수 있는 실패. 503으로 내보내 클라이언트가 재시도하게 한다. */
    static Stream<Arguments> 일시_실패() {
        return Stream.of(
                Arguments.of("스로틀링", ThrottlingException.builder().message("slow down").build()),
                Arguments.of("서비스 불가", ServiceUnavailableException.builder().build()),
                Arguments.of("AWS 내부 오류", InternalServerException.builder().build()),
                Arguments.of("전체 호출 타임아웃", ApiCallTimeoutException.create(1_000L)),
                Arguments.of("단일 시도 타임아웃", ApiCallAttemptTimeoutException.create(1_000L)),
                Arguments.of("재시도 가능 표시", RetryableException.create("retry me")),
                Arguments.of(
                        "네트워크 끊김",
                        SdkClientException.create(
                                "Unable to execute HTTP request",
                                new IOException("Connection reset"))),
                Arguments.of(
                        "소켓 타임아웃",
                        SdkClientException.create(
                                "timeout", new SocketTimeoutException("read timed out"))),
                Arguments.of(
                        "중첩된 네트워크 오류",
                        SdkClientException.create(
                                "wrapped",
                                new RuntimeException("layer", new IOException("Broken pipe")))));
    }

    /** 사람이 손대야 풀리는 실패. 재시도해도 똑같이 실패하므로 502로 내보낸다. */
    static Stream<Arguments> 영구_실패() {
        return Stream.of(
                Arguments.of(
                        "권한 부족", AccessDeniedException.builder().message("not authorized").build()),
                Arguments.of("잘못된 요청", ValidationException.builder().build()),
                Arguments.of("채널 한도 초과", ServiceQuotaExceededException.builder().build()),
                Arguments.of(
                        "자격증명 없음",
                        SdkClientException.create(
                                "Unable to load credentials from any of the providers")),
                Arguments.of("예상 못 한 오류", new IllegalStateException("boom")));
    }

    @Test
    @DisplayName("채널 생성에 성공하면 ARN·재생 URL·송출정보를 함께 반환한다")
    void 생성_성공() {
        givenCreateChannelReturns(
                CreateChannelResponse.builder()
                        .channel(
                                Channel.builder()
                                        .arn(CHANNEL_ARN)
                                        .playbackUrl("https://playback.example.com/abc.m3u8")
                                        .ingestEndpoint("ingest.example.com")
                                        .build())
                        .streamKey(StreamKey.builder().value("sk-secret").build())
                        .build());

        StreamingChannel channel = client.createChannel(CHANNEL_NAME);

        assertThat(channel.channelArn()).isEqualTo(CHANNEL_ARN);
        assertThat(channel.playbackUrl()).isEqualTo("https://playback.example.com/abc.m3u8");
        assertThat(channel.credential().ingestEndpoint()).isEqualTo("ingest.example.com");
        assertThat(channel.credential().streamKey()).isEqualTo("sk-secret");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("일시_실패")
    @DisplayName("생성 중 일시 실패는 LIVE_STREAMING_TEMPORARILY_UNAVAILABLE로 바꾼다")
    void 생성_일시_실패(String 상황, Throwable 원인) {
        givenCreateChannelThrows(원인);

        assertThatThrownBy(() -> client.createChannel(CHANNEL_NAME))
                .isInstanceOf(CustomException.class)
                .hasCause(원인)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("영구_실패")
    @DisplayName("생성 중 영구 실패는 LIVE_CHANNEL_CREATE_FAILED로 바꾼다")
    void 생성_영구_실패(String 상황, Throwable 원인) {
        givenCreateChannelThrows(원인);

        assertThatThrownBy(() -> client.createChannel(CHANNEL_NAME))
                .isInstanceOf(CustomException.class)
                .hasCause(원인)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_CHANNEL_CREATE_FAILED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("일시_실패")
    @DisplayName("삭제 중 일시 실패는 LIVE_STREAMING_TEMPORARILY_UNAVAILABLE로 바꾼다")
    void 삭제_일시_실패(String 상황, Throwable 원인) {
        givenDeleteChannelThrows(원인);

        assertThatThrownBy(() -> client.deleteChannel(CHANNEL_ARN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("영구_실패")
    @DisplayName("삭제 중 영구 실패는 LIVE_CHANNEL_DELETE_FAILED로 바꾼다")
    void 삭제_영구_실패(String 상황, Throwable 원인) {
        givenDeleteChannelThrows(원인);

        assertThatThrownBy(() -> client.deleteChannel(CHANNEL_ARN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_CHANNEL_DELETE_FAILED);
    }

    @Test
    @DisplayName("원인 사슬이 10단계를 넘으면 더 파고들지 않고 영구 실패로 본다")
    void 원인_사슬_탐색은_10단계에서_멈춘다() {
        Throwable deep = new IOException("맨 밑의 네트워크 오류");
        for (int i = 0; i < 11; i++) {
            deep = new RuntimeException("layer " + i, deep);
        }
        givenCreateChannelThrows(SdkClientException.create("wrapped", deep));

        assertThatThrownBy(() -> client.createChannel(CHANNEL_NAME))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_CHANNEL_CREATE_FAILED);
    }

    @Test
    @DisplayName("예외 메시지에 AWS 내부 사정을 담지 않는다")
    void 예외_메시지에_내부_사정을_담지_않는다() {
        givenCreateChannelThrows(
                AccessDeniedException.builder()
                        .message("User: arn:aws:iam::1234:user/dev is not authorized")
                        .build());

        assertThatThrownBy(() -> client.createChannel(CHANNEL_NAME))
                .hasMessage(LiveErrorCode.LIVE_CHANNEL_CREATE_FAILED.message())
                .hasMessageNotContaining("arn:aws:iam");
    }

    @SuppressWarnings("unchecked")
    private void givenCreateChannelReturns(CreateChannelResponse response) {
        given(ivsClient.createChannel(any(Consumer.class))).willReturn(response);
    }

    @SuppressWarnings("unchecked")
    private void givenCreateChannelThrows(Throwable failure) {
        given(ivsClient.createChannel(any(Consumer.class))).willThrow(failure);
    }

    @SuppressWarnings("unchecked")
    private void givenDeleteChannelThrows(Throwable failure) {
        given(ivsClient.deleteChannel(any(Consumer.class))).willThrow(failure);
    }

    /** 재발급에서는 ServiceQuotaExceeded가 경쟁 상황을 뜻해 따로 처리하므로 영구 실패에서 뺀다. */
    static Stream<Arguments> 영구_실패_재발급() {
        return 영구_실패().filter(
                        arguments ->
                                !(arguments.get()[1] instanceof ServiceQuotaExceededException));
    }

    @Test
    @DisplayName("재발급은 기존 키를 지운 뒤에 새 키를 만든다")
    void 재발급은_삭제_후_생성_순서다() {
        givenStreamKeyExists("arn:key/old");
        givenCreateStreamKeyReturns("sk-new");
        givenGetChannelReturns("ingest.example.com");

        BroadcastCredential credential = client.reissueCredential(CHANNEL_ARN);

        InOrder order = inOrder(ivsClient);
        order.verify(ivsClient).listStreamKeys(any(Consumer.class));
        order.verify(ivsClient).deleteStreamKey(any(Consumer.class));
        order.verify(ivsClient).createStreamKey(any(Consumer.class));
        assertThat(credential.streamKey()).isEqualTo("sk-new");
        assertThat(credential.ingestEndpoint()).isEqualTo("ingest.example.com");
    }

    @Test
    @DisplayName("지우려던 키가 이미 없어도 새 키 발급까지 진행한다")
    void 이미_지워진_키는_넘어간다() {
        givenStreamKeyExists("arn:key/old");
        given(ivsClient.deleteStreamKey(any(Consumer.class)))
                .willThrow(ResourceNotFoundException.builder().build());
        givenCreateStreamKeyReturns("sk-new");
        givenGetChannelReturns("ingest.example.com");

        assertThat(client.reissueCredential(CHANNEL_ARN).streamKey()).isEqualTo("sk-new");
    }

    @Test
    @DisplayName("지운 직후 다른 요청이 키를 먼저 만들었으면 재발급 충돌로 바꾼다")
    void 재발급_경쟁은_충돌로_바꾼다() {
        givenStreamKeyExists("arn:key/old");
        given(ivsClient.createStreamKey(any(Consumer.class)))
                .willThrow(ServiceQuotaExceededException.builder().build());

        assertThatThrownBy(() -> client.reissueCredential(CHANNEL_ARN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_CREDENTIAL_REISSUE_CONFLICT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("일시_실패")
    @DisplayName("재발급 중 일시 실패는 LIVE_STREAMING_TEMPORARILY_UNAVAILABLE로 바꾼다")
    void 재발급_일시_실패(String 상황, Throwable 원인) {
        given(ivsClient.listStreamKeys(any(Consumer.class))).willThrow(원인);

        assertThatThrownBy(() -> client.reissueCredential(CHANNEL_ARN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("영구_실패_재발급")
    @DisplayName("재발급 중 영구 실패는 LIVE_CREDENTIAL_REISSUE_FAILED로 바꾼다")
    void 재발급_영구_실패(String 상황, Throwable 원인) {
        given(ivsClient.listStreamKeys(any(Consumer.class))).willThrow(원인);

        assertThatThrownBy(() -> client.reissueCredential(CHANNEL_ARN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_CREDENTIAL_REISSUE_FAILED);
    }

    @Test
    @DisplayName("스트림 키 삭제는 채널에 달린 키를 모두 지운다")
    void 스트림_키를_모두_지운다() {
        givenStreamKeyExists("arn:key/a", "arn:key/b");

        client.deleteStreamKeys(CHANNEL_ARN);

        verify(ivsClient, times(2)).deleteStreamKey(any(Consumer.class));
    }

    @Test
    @DisplayName("송출 중이면 BROADCASTING이다")
    void 송출_중이면_BROADCASTING이다() {
        given(ivsClient.getStream(any(Consumer.class)))
                .willReturn(GetStreamResponse.builder().build());

        assertThat(client.getStreamState(CHANNEL_ARN)).isEqualTo(StreamState.BROADCASTING);
    }

    @Test
    @DisplayName("송출하지 않는 채널은 예외가 아니라 NOT_BROADCASTING이다")
    void 미송출은_예외가_아니다() {
        given(ivsClient.getStream(any(Consumer.class)))
                .willThrow(ChannelNotBroadcastingException.builder().build());

        assertThat(client.getStreamState(CHANNEL_ARN)).isEqualTo(StreamState.NOT_BROADCASTING);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("영구_실패")
    @DisplayName("상태 조회 중 영구 실패는 LIVE_STREAM_STATUS_FETCH_FAILED로 바꾼다")
    void 상태_조회_영구_실패(String 상황, Throwable 원인) {
        given(ivsClient.getStream(any(Consumer.class))).willThrow(원인);

        assertThatThrownBy(() -> client.getStreamState(CHANNEL_ARN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_STREAM_STATUS_FETCH_FAILED);
    }

    @Test
    @DisplayName("송출 중이 아닌 채널에 중단을 요청해도 예외가 나지 않는다")
    void 중단_중복_호출은_통과한다() {
        given(ivsClient.stopStream(any(Consumer.class)))
                .willThrow(ChannelNotBroadcastingException.builder().build());

        client.stopStream(CHANNEL_ARN);
    }

    @Test
    @DisplayName("송출 중이면 중단을 요청한다")
    void 송출_중이면_중단한다() {
        given(ivsClient.stopStream(any(Consumer.class)))
                .willReturn(StopStreamResponse.builder().build());

        client.stopStream(CHANNEL_ARN);

        verify(ivsClient).stopStream(any(Consumer.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("영구_실패")
    @DisplayName("중단 중 영구 실패는 LIVE_BROADCAST_STOP_FAILED로 바꾼다")
    void 중단_영구_실패(String 상황, Throwable 원인) {
        given(ivsClient.stopStream(any(Consumer.class))).willThrow(원인);

        assertThatThrownBy(() -> client.stopStream(CHANNEL_ARN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_BROADCAST_STOP_FAILED);
    }

    @SuppressWarnings("unchecked")
    private void givenStreamKeyExists(String... keyArns) {
        List<StreamKeySummary> summaries =
                Stream.of(keyArns)
                        .map(keyArn -> StreamKeySummary.builder().arn(keyArn).build())
                        .toList();
        given(ivsClient.listStreamKeys(any(Consumer.class)))
                .willReturn(ListStreamKeysResponse.builder().streamKeys(summaries).build());
    }

    @SuppressWarnings("unchecked")
    private void givenCreateStreamKeyReturns(String value) {
        given(ivsClient.createStreamKey(any(Consumer.class)))
                .willReturn(
                        CreateStreamKeyResponse.builder()
                                .streamKey(StreamKey.builder().value(value).build())
                                .build());
    }

    @SuppressWarnings("unchecked")
    private void givenGetChannelReturns(String ingestEndpoint) {
        given(ivsClient.getChannel(any(Consumer.class)))
                .willReturn(
                        GetChannelResponse.builder()
                                .channel(Channel.builder().ingestEndpoint(ingestEndpoint).build())
                                .build());
    }
}
