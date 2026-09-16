package com.toasty.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** point3 결제 취소 응답. 취소 한 건의 처리 결과다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentRefundResponse(
        String id,
        String paymentSessionId,
        String status,
        Integer amount,
        Integer taxFreeAmount,
        Integer vat,
        Integer fee,
        String reason) {

    private static final String COMPLETED = "completed";

    /** 취소가 끝났는지 판단한다. 끝나지 않았으면 상태를 다시 조회해야 한다. */
    public boolean isCompleted() {
        return COMPLETED.equalsIgnoreCase(status);
    }
}
