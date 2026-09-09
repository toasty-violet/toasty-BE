package com.toasty.domain.live.exception;

import com.toasty.domain.live.entity.Live;
import com.toasty.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LiveErrorCode implements ErrorCode {
    LIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "LIVE_NOT_FOUND", "라이브를 찾을 수 없습니다."),
    LIVE_FORBIDDEN(HttpStatus.FORBIDDEN, "LIVE_FORBIDDEN", "해당 라이브에 대한 권한이 없습니다."),
    LIVE_ALREADY_ENDED(HttpStatus.CONFLICT, "LIVE_ALREADY_ENDED", "이미 종료된 라이브입니다."),
    LIVE_NOT_EDITABLE(HttpStatus.CONFLICT, "LIVE_NOT_EDITABLE", "방송이 시작된 라이브는 수정할 수 없습니다."),
    LIVE_NOT_DELETABLE(HttpStatus.CONFLICT, "LIVE_NOT_DELETABLE", "방송이 시작된 라이브는 삭제할 수 없습니다."),
    LIVE_SCHEDULE_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "LIVE_SCHEDULE_LIMIT_EXCEEDED",
            "예정된 라이브는 " + Live.MAX_SCHEDULED + "개까지 만들 수 있습니다. 기존 라이브를 방송하거나 삭제한 뒤 다시 시도해주세요."),
    LIVE_NOT_BROADCASTING(
            HttpStatus.CONFLICT, "LIVE_NOT_BROADCASTING", "방송 중일 때만 상품을 고정하거나 수정할 수 있습니다."),
    LIVE_ALREADY_BROADCASTING(
            HttpStatus.CONFLICT, "LIVE_ALREADY_BROADCASTING", "이미 진행 중인 라이브가 있습니다."),
    LIVE_CREDENTIAL_REISSUE_CONFLICT(
            HttpStatus.CONFLICT,
            "LIVE_CREDENTIAL_REISSUE_CONFLICT",
            "다른 재발급 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."),
    LIVE_CHANNEL_CREATE_FAILED(
            HttpStatus.BAD_GATEWAY, "LIVE_CHANNEL_CREATE_FAILED", "방송 채널 생성에 실패했습니다."),
    LIVE_CHANNEL_DELETE_FAILED(
            HttpStatus.BAD_GATEWAY, "LIVE_CHANNEL_DELETE_FAILED", "방송 채널 삭제에 실패했습니다."),
    LIVE_CHAT_ROOM_CREATE_FAILED(
            HttpStatus.BAD_GATEWAY, "LIVE_CHAT_ROOM_CREATE_FAILED", "채팅방 생성에 실패했습니다."),
    LIVE_CHAT_TOKEN_ISSUE_FAILED(
            HttpStatus.BAD_GATEWAY, "LIVE_CHAT_TOKEN_ISSUE_FAILED", "채팅 입장 토큰 발급에 실패했습니다."),
    LIVE_CHAT_ROOM_NOT_FOUND(
            HttpStatus.NOT_FOUND, "LIVE_CHAT_ROOM_NOT_FOUND", "이 라이브에는 채팅방이 없습니다."),
    // 정리는 실패해도 요청을 뒤집지 않아 지금은 응답으로 나가지 않는다. 로그에서 원인을 가르는 데 쓴다.
    // 아래 LIVE_CHANNEL_DELETE_FAILED도 같은 자리다.
    LIVE_CHAT_ROOM_DELETE_FAILED(
            HttpStatus.BAD_GATEWAY, "LIVE_CHAT_ROOM_DELETE_FAILED", "채팅방 삭제에 실패했습니다."),
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
