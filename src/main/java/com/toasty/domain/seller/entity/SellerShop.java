package com.toasty.domain.seller.entity;

/** 다른 도메인에 넘기는 스토어 정보. 스토어 이름은 users.nickname에 있어 여기에 없다. */
public record SellerShop(Long sellerId, Long userId, String shopImageUrl) {}
