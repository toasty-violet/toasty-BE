package com.toasty.domain.user.exception;

import com.toasty.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    // 유저 조회 실패
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 유저입니다."),

    // 다른 유저가 이미 쓰고 있는 닉네임으로 온보딩을 제출한 경우
    USER_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "USER_NICKNAME_DUPLICATED", "이미 사용 중인 닉네임입니다."),

    // 이미 역할이 정해진 유저가 온보딩을 다시 제출한 경우
    USER_ONBOARDING_ALREADY_COMPLETED(
            HttpStatus.CONFLICT, "USER_ONBOARDING_ALREADY_COMPLETED", "이미 온보딩을 마친 유저입니다.");

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
