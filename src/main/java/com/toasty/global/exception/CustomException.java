package com.toasty.global.exception;

import lombok.Getter;

/** 비즈니스 예외의 유일한 타입. 종류는 {@link ErrorCode}로 구분한다. */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public CustomException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }
}
