package com.toasty.domain.payment.client.dto;

/**
 * point3 결제 세션 생성 요청 본문. displayMerchantName을 보내지 않으면 결제창에 productName이 표시된다.
 *
 * <p>여기에 없는 필드를 넣으면 point3가 400으로 막는다. payerId도 이 본문으로는 보낼 수 없고, 프론트가 결제창 인증 URL을 만들 때 쓴다.
 */
public record PaymentSessionCreateRequest(int amount, String productName) {}
