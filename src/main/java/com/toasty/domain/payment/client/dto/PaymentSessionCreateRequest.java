package com.toasty.domain.payment.client.dto;

/** point3 결제 세션 생성 요청 본문. displayMerchantName을 보내지 않으면 결제창에 productName이 표시된다. */
public record PaymentSessionCreateRequest(int amount, String productName) {}
