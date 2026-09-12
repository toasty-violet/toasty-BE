package com.toasty.domain.order.entity;

import java.util.List;

/** 주문 화면 위의 상태 칩. 셀러 주문탭과 구매자 주문내역이 같이 쓴다. */
public enum OrderStatusFilter {
    ALL(List.of(OrderStatus.SHIPPING_PENDING, OrderStatus.SHIPPED)),
    SHIPPING_PENDING(List.of(OrderStatus.SHIPPING_PENDING)),
    SHIPPED(List.of(OrderStatus.SHIPPED));

    private final List<OrderStatus> statuses;

    OrderStatusFilter(List<OrderStatus> statuses) {
        this.statuses = statuses;
    }

    public List<OrderStatus> statuses() {
        return statuses;
    }
}
