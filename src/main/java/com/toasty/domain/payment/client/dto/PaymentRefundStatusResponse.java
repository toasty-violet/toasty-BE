package com.toasty.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** point3 결제 취소 상태 응답. 원결제 금액과 남은 취소 가능 금액, 지금까지의 취소 내역을 함께 준다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentRefundStatusResponse(
        String paymentSessionId,
        String productName,
        Integer originalAmount,
        Integer refundableAmount,
        Boolean canCreateRefund,
        String status,
        List<Item> refunds) {

    private static final String PROCESSING = "processing";

    /** 취소 내역 한 줄. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String id,
            String status,
            Integer amount,
            Integer taxFreeAmount,
            Integer vat,
            String reason) {}

    /** 끝나지 않은 취소가 남아 새 취소를 걸 수 없는 상태인지 판단한다. */
    public boolean isProcessing() {
        return PROCESSING.equalsIgnoreCase(status);
    }

    /** 이 금액까지만 더 취소할 수 있다. point3가 값을 주지 않으면 취소할 수 없는 것으로 본다. */
    public int refundable() {
        return refundableAmount == null ? 0 : refundableAmount;
    }
}
