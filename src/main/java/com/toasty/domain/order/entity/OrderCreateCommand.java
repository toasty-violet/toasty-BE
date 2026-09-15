package com.toasty.domain.order.entity;

/** 주문하기 요청. 금액은 받지 않는다. 상품 가격은 서버가 상품에서 읽어 계산한다. */
public record OrderCreateCommand(
        Long userId, Long customerId, Long productId, Long liveId, int quantity) {}
