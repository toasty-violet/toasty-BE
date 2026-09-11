package com.toasty.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** point3가 반환한 결제 세션. id가 결제창을 열 때 쓰는 sessionId다. payerId는 본인인증을 마친 세션에만 있다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentSessionResponse(String id, String status, Integer amount, String payerId) {

    /** 본인인증이 끝나 payerId를 꺼낼 수 있는 세션인지 판단한다. */
    public boolean hasPayerId() {
        return payerId != null && !payerId.isBlank();
    }
}
