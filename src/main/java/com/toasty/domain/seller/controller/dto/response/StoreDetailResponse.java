package com.toasty.domain.seller.controller.dto.response;

/** 구매자가 보는 스토어 화면에서 판매자 행이 들고 있는 값. 팔로워 수·상품 수·팔로우 여부는 다른 도메인이 채운다. */
public record StoreDetailResponse(
        Long sellerId, String shopImageUrl, String shopName, String description) {}
