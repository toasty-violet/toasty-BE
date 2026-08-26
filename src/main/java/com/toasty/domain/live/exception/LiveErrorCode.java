package com.toasty.domain.live.exception;

import com.toasty.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LiveErrorCode implements ErrorCode {
    LIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "LIVE_NOT_FOUND", "라이브를 찾을 수 없습니다."),
    LIVE_CHANNEL_CREATE_FAILED(
            HttpStatus.BAD_GATEWAY, "LIVE_CHANNEL_CREATE_FAILED", "방송 채널 생성에 실패했습니다."),
    LIVE_CHANNEL_DELETE_FAILED(
            HttpStatus.BAD_GATEWAY, "LIVE_CHANNEL_DELETE_FAILED", "방송 채널 삭제에 실패했습니다."),
    LIVE_CREDENTIAL_REISSUE_FAILED(
            HttpStatus.BAD_GATEWAY, "LIVE_CREDENTIAL_REISSUE_FAILED", "송출정보 재발급에 실패했습니다."),
    LIVE_STREAM_KEY_DELETE_FAILED(
            HttpStatus.BAD_GATEWAY, "LIVE_STREAM_KEY_DELETE_FAILED", "송출 키 삭제에 실패했습니다."),
    LIVE_STREAM_STATUS_FETCH_FAILED(
            HttpStatus.BAD_GATEWAY, "LIVE_STREAM_STATUS_FETCH_FAILED", "송출 상태 조회에 실패했습니다."),
    LIVE_BROADCAST_STOP_FAILED(
            HttpStatus.BAD_GATEWAY, "LIVE_BROADCAST_STOP_FAILED", "방송 중단에 실패했습니다."),
    LIVE_STREAMING_TEMPORARILY_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "LIVE_STREAMING_TEMPORARILY_UNAVAILABLE",
            "방송 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
