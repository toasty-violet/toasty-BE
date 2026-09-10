package com.toasty.domain.order.repository;

import com.toasty.domain.order.entity.OrderStatus;

/** 상태별 주문 수. 상태마다 세지 않고 한 번에 묶어 가져올 때 쓴다. */
public interface SellerOrderCount {

    OrderStatus getStatus();

    int getOrderCount();
}
