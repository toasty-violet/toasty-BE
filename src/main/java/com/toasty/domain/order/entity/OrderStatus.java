package com.toasty.domain.order.entity;

/** 주문 상태. 결제가 끝나면 배송대기, 운송장이 등록되면 발송완료다. */
public enum OrderStatus {
    SHIPPING_PENDING,
    SHIPPED
}
