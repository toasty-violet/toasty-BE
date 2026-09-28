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
            "결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_SESSION_QUERY_FAILED(
            HttpStatus.BAD_GATEWAY, "PAYMENT_SESSION_QUERY_FAILED", "결제 세션 조회에 실패했습니다."),
    PAYMENT_SESSION_NOT_FOUND(
            HttpStatus.NOT_FOUND, "PAYMENT_SESSION_NOT_FOUND", "결제 세션을 찾을 수 없습니다."),
    PAYMENT_SESSION_OWNER_MISMATCH(
            HttpStatus.FORBIDDEN, "PAYMENT_SESSION_OWNER_MISMATCH", "본인이 만든 결제 세션이 아닙니다."),
    PAYMENT_PAYER_ID_NOT_READY(
            HttpStatus.CONFLICT,
            "PAYMENT_PAYER_ID_NOT_READY",
            "계좌 등록이 끝나지 않았습니다. 결제를 완료한 뒤 다시 시도해주세요."),
    PAYMENT_CAPTURE_FAILED(
            HttpStatus.BAD_GATEWAY, "PAYMENT_CAPTURE_FAILED", "결제 승인 요청이 처리되지 않았습니다."),
    // 재시도와 조회로도 결과를 가리지 못한 경우다. 실패로 확정하지 말고 이 상태를 그대로 알려준다.
    PAYMENT_RESULT_UNKNOWN(
            HttpStatus.SERVICE_UNAVAILABLE,
            "PAYMENT_RESULT_UNKNOWN",
            "결제 결과를 확인하지 못했습니다. 잠시 후 주문내역에서 확인해주세요."),
    PAYMENT_REJECTED(HttpStatus.CONFLICT, "PAYMENT_REJECTED", "결제가 승인되지 않았습니다."),
    // 주문 금액과 세션 금액이 어긋난 경우다. 승인을 보내지 않고 막는다.
    PAYMENT_AMOUNT_MISMATCH(
            HttpStatus.CONFLICT, "PAYMENT_AMOUNT_MISMATCH", "주문 금액과 결제 금액이 일치하지 않습니다."),
    PAYMENT_ALREADY_CAPTURED(HttpStatus.CONFLICT, "PAYMENT_ALREADY_CAPTURED", "이미 결제가 끝났습니다."),
    PAYMENT_REFUND_FAILED(HttpStatus.BAD_GATEWAY, "PAYMENT_REFUND_FAILED", "결제 취소에 실패했습니다."),
    PAYMENT_REFUND_IN_PROGRESS(
            HttpStatus.CONFLICT, "PAYMENT_REFUND_IN_PROGRESS", "처리 중인 취소가 있어 새로 취소할 수 없습니다."),
    PAYMENT_REFUND_WINDOW_BLOCKED(
            HttpStatus.CONFLICT,
            "PAYMENT_REFUND_WINDOW_BLOCKED",
            "결제 마감 시간대에는 취소할 수 없습니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_REFUND_AMOUNT_EXCEEDED(
            HttpStatus.CONFLICT, "PAYMENT_REFUND_AMOUNT_EXCEEDED", "취소할 수 있는 금액을 넘었습니다."),
    PAYMENT_REFUND_STATUS_QUERY_FAILED(
            HttpStatus.BAD_GATEWAY, "PAYMENT_REFUND_STATUS_QUERY_FAILED", "결제 취소 상태 조회에 실패했습니다.");

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
