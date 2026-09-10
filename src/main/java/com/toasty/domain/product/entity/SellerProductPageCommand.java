package com.toasty.domain.product.entity;

/** 셀러 상품탭 목록 조회. cursor가 null이면 첫 페이지다. */
public record SellerProductPageCommand(Long sellerId, SellerProductFilter filter, Long cursor) {}
