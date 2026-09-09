package com.toasty.domain.product.entity;

/** 방송 중 상품 고정 요청. Long이 나란히 오는 자리라 이름을 붙여 순서 실수를 막는다. */
public record LiveProductPinCommand(Long liveId, Long productId, Long sellerId) {}
