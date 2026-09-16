package com.toasty.domain.order.entity;

/** 라이브 한 건에서 나온 판매 집계. 결제까지 끝난 주문만 담는다. */
public record LiveSalesStat(long orderCount, long salesAmount, long soldQuantity) {

    public static LiveSalesStat empty() {
        return new LiveSalesStat(0, 0, 0);
    }
}
