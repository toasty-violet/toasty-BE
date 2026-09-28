package com.toasty.domain.payment.entity;

/** 승인이 끝난 결제를 취소할 때 필요한 값. 면세금액과 부가세는 금액에서 계산해 채운다. */
public record PaymentRefundCommand(Long orderId, String sessionId, int amount, String reason) {}
