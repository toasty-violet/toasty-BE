package com.toasty.domain.order.entity;

/** 셀러 주문탭 목록 조회. cursor가 null이면 첫 페이지다. */
public record SellerOrderPageCommand(Long sellerId, SellerOrderFilter filter, Long cursor) {}
