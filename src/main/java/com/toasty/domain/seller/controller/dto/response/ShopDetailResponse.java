package com.toasty.domain.seller.controller.dto.response;

/** 스토어 관리 화면에서 판매자 행이 들고 있는 값. 팔로워 수·상품 수는 다른 도메인이 센다. */
public record ShopDetailResponse(
        Long sellerId,
        String shopImageUrl,
        String shopImageObjectKey,
        String shopName,
        String description,
        ShopSalesSummaryResponse salesSummary,
        ShopShippingFeeResponse shippingFee) {}
