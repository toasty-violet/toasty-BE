package com.toasty.domain.order.exception;

import com.toasty.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    ORDER_ALREADY_SHIPPED(HttpStatus.CONFLICT, "ORDER_ALREADY_SHIPPED", "이미 발송완료된 주문입니다."),
    // 결제 대기 중인 주문만 승인할 수 있다
    ORDER_NOT_PAYABLE(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "결제를 진행할 수 있는 주문이 아닙니다."),
    ORDER_SESSION_NOT_LINKED(
            HttpStatus.CONFLICT, "ORDER_SESSION_NOT_LINKED", "결제 세션이 준비되지 않은 주문입니다."),
    ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT, "ORDER_NOT_CANCELABLE", "취소할 수 있는 주문이 아닙니다.");

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
