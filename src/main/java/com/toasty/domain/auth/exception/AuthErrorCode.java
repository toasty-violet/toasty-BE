package com.toasty.domain.auth.exception;

import com.toasty.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    KAKAO_TOKEN_REQUEST_FAILED(
            HttpStatus.BAD_GATEWAY, "AUTH_KAKAO_TOKEN_REQUEST_FAILED", "카카오 토큰 발급에 실패했습니다."),
    KAKAO_USER_INFO_REQUEST_FAILED(
            HttpStatus.BAD_GATEWAY,
            "AUTH_KAKAO_USER_INFO_REQUEST_FAILED",
            "카카오 사용자 정보 조회에 실패했습니다."),
    REFRESH_TOKEN_NOT_FOUND(
            HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_NOT_FOUND", "만료되었거나 존재하지 않는 리프레시 토큰입니다."),
    REFRESH_TOKEN_REUSE_DETECTED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_REFRESH_TOKEN_REUSE_DETECTED",
            "이미 사용된 리프레시 토큰입니다. 안전을 위해 모든 기기에서 로그아웃되었으니 다시 로그인해 주세요.");

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
