package com.toasty.domain.live.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.toasty.domain.live.client.dto.ChatRole;
import com.toasty.domain.live.client.dto.ChatToken;
import com.toasty.domain.live.client.dto.ChatTokenCommand;
import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.global.exception.CustomException;
import java.io.IOException;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.RetryableException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ivschat.IvschatClient;
import software.amazon.awssdk.services.ivschat.model.AccessDeniedException;
import software.amazon.awssdk.services.ivschat.model.CreateChatTokenResponse;
import software.amazon.awssdk.services.ivschat.model.CreateRoomResponse;
import software.amazon.awssdk.services.ivschat.model.InternalServerException;
import software.amazon.awssdk.services.ivschat.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ivschat.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.ivschat.model.ThrottlingException;
import software.amazon.awssdk.services.ivschat.model.ValidationException;

@DisplayName("AwsIvsChatClient")
class AwsIvsChatClientTest {

    private static final String ROOM_NAME = "toasty-live-7-abcd1234";
    private static final String ROOM_ARN = "arn:aws:ivschat:ap-northeast-2:1:room/abcd";

    private IvschatClient ivschatClient;
    private AwsIvsChatClient client;

    @BeforeEach
    void setUp() {
        ivschatClient = mock(IvschatClient.class);
        client = new AwsIvsChatClient(ivschatClient);
    }

    @Test
    @DisplayName("방을 만들면 ARN을 돌려준다")
    @SuppressWarnings("unchecked")
    void 방을_만들면_ARN을_준다() {
        given(ivschatClient.createRoom(any(Consumer.class)))
                .willReturn(CreateRoomResponse.builder().arn(ROOM_ARN).build());

        assertThat(client.createRoom(ROOM_NAME)).isEqualTo(ROOM_ARN);
    }

    @Test
    @DisplayName("이미 없는 방을 지우는 것은 실패가 아니다")
    @SuppressWarnings("unchecked")
    void 없는_방_삭제는_통과한다() {
        given(ivschatClient.deleteRoom(any(Consumer.class)))
                .willThrow(ResourceNotFoundException.builder().build());

        assertThatCode(() -> client.deleteRoom(ROOM_ARN)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("토큰과 만료 시각을 그대로 옮겨 담는다")
    @SuppressWarnings("unchecked")
    void 토큰을_옮겨_담는다() {
        Instant expiresAt = Instant.parse("2026-09-09T10:00:00Z");
        given(ivschatClient.createChatToken(any(Consumer.class)))
                .willReturn(
                        CreateChatTokenResponse.builder()
                                .token("chat-token")
                                .tokenExpirationTime(expiresAt)
                                .sessionExpirationTime(expiresAt)
                                .build());

        ChatToken chatToken =
                client.createToken(
                        new ChatTokenCommand(ROOM_ARN, "user-9", "토스티샵", ChatRole.SELLER, true));

        assertThat(chatToken.token()).isEqualTo("chat-token");
        assertThat(chatToken.expiresAt()).isEqualTo(expiresAt);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("일시_실패")
    @DisplayName("토큰 발급의 일시 실패도 다시 시도하도록 알린다")
    @SuppressWarnings("unchecked")
    void 토큰_일시_실패(String 상황, Throwable 원인) {
        given(ivschatClient.createChatToken(any(Consumer.class))).willThrow(원인);

        assertThatThrownBy(
                        () ->
                                client.createToken(
                                        new ChatTokenCommand(
                                                ROOM_ARN, "user-9", null, ChatRole.CUSTOMER, true)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("영구_실패")
    @DisplayName("토큰 발급의 영구 실패는 발급 실패로 바꾼다")
    @SuppressWarnings("unchecked")
    void 토큰_영구_실패(String 상황, Throwable 원인) {
        given(ivschatClient.createChatToken(any(Consumer.class))).willThrow(원인);

        assertThatThrownBy(
                        () ->
                                client.createToken(
                                        new ChatTokenCommand(
                                                ROOM_ARN, "user-9", null, ChatRole.CUSTOMER, true)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_CHAT_TOKEN_ISSUE_FAILED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("일시_실패")
    @DisplayName("일시 실패는 다시 시도하도록 알린다")
    @SuppressWarnings("unchecked")
    void 일시_실패는_재시도를_알린다(String 상황, Throwable 원인) {
        given(ivschatClient.createRoom(any(Consumer.class))).willThrow(원인);

        assertThatThrownBy(() -> client.createRoom(ROOM_NAME))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("영구_실패")
    @DisplayName("영구 실패는 생성 실패로 바꾼다")
    @SuppressWarnings("unchecked")
    void 영구_실패는_생성_실패다(String 상황, Throwable 원인) {
        given(ivschatClient.createRoom(any(Consumer.class))).willThrow(원인);

        assertThatThrownBy(() -> client.createRoom(ROOM_NAME))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(LiveErrorCode.LIVE_CHAT_ROOM_CREATE_FAILED);
    }

    static Stream<Arguments> 일시_실패() {
        return Stream.of(
                Arguments.of("스로틀링", ThrottlingException.builder().build()),
                Arguments.of("AWS 내부 오류", InternalServerException.builder().build()),
                Arguments.of("호출 타임아웃", ApiCallTimeoutException.create(1_000L)),
                Arguments.of("재시도 가능 표시", RetryableException.create("retry me")),
                Arguments.of(
                        "네트워크 끊김",
                        SdkClientException.create(
                                "Unable to execute HTTP request",
                                new IOException("Connection reset"))));
    }

    // 방 한도 초과는 다시 불러도 풀리지 않는다. 일시 실패로 보면 무한히 재시도하게 된다.
    static Stream<Arguments> 영구_실패() {
        return Stream.of(
                Arguments.of("권한 부족", AccessDeniedException.builder().build()),
                Arguments.of("잘못된 요청", ValidationException.builder().build()),
                Arguments.of("방 한도 초과", ServiceQuotaExceededException.builder().build()),
                Arguments.of(
                        "자격증명 없음",
                        SdkClientException.create(
                                "Unable to load credentials from any of the providers")),
                Arguments.of("예상 못 한 오류", new IllegalStateException("boom")));
    }
}
