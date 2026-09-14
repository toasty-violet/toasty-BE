package com.toasty.domain.order.entity;

/** 구매자 주문내역 목록 조회. cursor가 null이면 첫 페이지다. */
public record CustomerOrderPageCommand(Long customerId, OrderStatusFilter filter, Long cursor) {}
