package com.toasty.domain.order.entity;

import java.util.List;

/** 셀러 주문탭 위의 상태 칩. */
public enum SellerOrderFilter {
    ALL(List.of(OrderStatus.SHIPPING_PENDING, OrderStatus.SHIPPED)),
    SHIPPING_PENDING(List.of(OrderStatus.SHIPPING_PENDING)),
    SHIPPED(List.of(OrderStatus.SHIPPED));

    private final List<OrderStatus> statuses;

    SellerOrderFilter(List<OrderStatus> statuses) {
        this.statuses = statuses;
    }

    public List<OrderStatus> statuses() {
        return statuses;
    }
}
