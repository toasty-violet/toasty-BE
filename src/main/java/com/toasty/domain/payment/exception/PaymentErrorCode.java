package com.toasty.domain.payment.exception;

import com.toasty.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {
    PAYMENT_SESSION_CREATE_FAILED(
            HttpStatus.BAD_GATEWAY, "PAYMENT_SESSION_CREATE_FAILED", "결제 세션 생성에 실패했습니다."),
    PAYMENT_TEMPORARILY_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "PAYMENT_TEMPORARILY_UNAVAILABLE",
            "결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.");

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
