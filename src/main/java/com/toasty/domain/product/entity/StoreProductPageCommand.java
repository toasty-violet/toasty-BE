package com.toasty.domain.product.entity;

/** 스토어 화면의 상품 그리드 조회. cursor가 null이면 첫 페이지다. */
public record StoreProductPageCommand(Long sellerId, Long cursor) {}
