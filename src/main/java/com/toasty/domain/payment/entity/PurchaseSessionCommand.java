package com.toasty.domain.payment.entity;

/** 상품 결제용 세션을 만들 때 필요한 값. 금액은 서버가 상품에서 계산한 값이어야 한다. */
public record PurchaseSessionCommand(Long userId, Long orderId, int amount, String productName) {}
