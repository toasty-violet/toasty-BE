package com.toasty.domain.order.entity;

/** 주문 상태. 결제가 끝나면 배송대기, 운송장이 등록되면 발송완료다. */
public enum OrderStatus {
    // 주문을 만들고 결제 승인을 기다리는 상태. 재고는 이미 선점해 두었다
    PAYMENT_PENDING,
    // 승인이 거절돼 결제되지 않은 상태. 선점했던 재고는 되돌렸다
    PAYMENT_FAILED,
    SHIPPING_PENDING,
    SHIPPED,
    // 결제까지 끝난 주문을 서버가 되돌린 상태
    CANCELED
}
