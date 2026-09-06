package com.toasty.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** point3가 반환한 결제 세션. id가 결제창을 열 때 쓰는 sessionId다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentSessionResponse(String id, String status, Integer amount) {}
