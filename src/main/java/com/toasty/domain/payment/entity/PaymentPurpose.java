package com.toasty.domain.payment.entity;

/** 결제 세션을 만든 목적. 결제 승인을 호출할지가 갈리므로 세션을 만들 때 정해 저장한다. */
public enum PaymentPurpose {
    PAYER_ID_REGISTRATION,
    PRODUCT_PURCHASE
}
