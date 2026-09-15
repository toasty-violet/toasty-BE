package com.toasty.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** point3 결제 세션 한 건. 상태값은 소문자로 내려온다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentSessionResponse(String id, String status, Integer amount, String payerId) {

    private static final String CAPTURED = "captured";
    private static final String FAILED = "failed";

    // 문서에 없지만 승인 기한을 넘긴 세션에서 실제로 내려온다.
    private static final String EXPIRED = "expired";

    /** 본인인증이 끝나 payerId를 꺼낼 수 있는 세션인지 판단한다. */
    public boolean hasPayerId() {
        return payerId != null && !payerId.isBlank();
    }

    /** 결제가 끝난 세션인지 판단한다. 승인 응답을 받지 못했을 때 이 값으로 결과를 가른다. */
    public boolean isCaptured() {
        return CAPTURED.equalsIgnoreCase(status);
    }

    /** 다시 승인해도 결제가 되지 않는 세션인지 판단한다. */
    public boolean isFailed() {
        return FAILED.equalsIgnoreCase(status) || EXPIRED.equalsIgnoreCase(status);
    }
}
