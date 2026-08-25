package com.toasty.domain.live.exception;

import com.toasty.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LiveErrorCode implements ErrorCode {
    LIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "LIVE_NOT_FOUND", "라이브를 찾을 수 없습니다."),
    LIVE_FORBIDDEN(HttpStatus.FORBIDDEN, "LIVE_FORBIDDEN", "해당 라이브에 대한 권한이 없습니다.");

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
