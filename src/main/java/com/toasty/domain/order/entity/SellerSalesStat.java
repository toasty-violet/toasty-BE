package com.toasty.domain.order.entity;

/** 스토어 한 곳의 누적 판매 집계. 결제까지 끝난 주문만 담는다. */
public record SellerSalesStat(long orderCount, long buyerCount, long salesAmount) {}
