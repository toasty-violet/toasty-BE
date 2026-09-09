package com.toasty.domain.customer.exception;

import com.toasty.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CustomerErrorCode implements ErrorCode {

    // 구매자 조회 실패
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "존재하지 않는 구매자입니다."),

    // 온보딩에서 만들어졌어야 할 기본 배송지가 없는 경우
    CUSTOMER_ADDRESS_NOT_FOUND(
            HttpStatus.NOT_FOUND, "CUSTOMER_ADDRESS_NOT_FOUND", "등록된 배송지가 없습니다.");

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
