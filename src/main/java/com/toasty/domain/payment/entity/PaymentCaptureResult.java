package com.toasty.domain.payment.entity;

import java.time.LocalDateTime;

/**
 * 결제 승인 결과. 승인됐는지와, 거절됐다면 그 사유를 담는다.
 *
 * <p>결과를 가리지 못한 경우는 이 값으로 돌려주지 않는다. 실패로 읽힐 수 있어 예외로 올린다.
 */
public record PaymentCaptureResult(
        boolean captured, LocalDateTime capturedAt, String outcomeCode, String failureMessage) {

    public static PaymentCaptureResult captured(LocalDateTime capturedAt, String outcomeCode) {
        return new PaymentCaptureResult(true, capturedAt, outcomeCode, null);
    }

    public static PaymentCaptureResult rejected(String outcomeCode, String failureMessage) {
        return new PaymentCaptureResult(false, null, outcomeCode, failureMessage);
    }
}
