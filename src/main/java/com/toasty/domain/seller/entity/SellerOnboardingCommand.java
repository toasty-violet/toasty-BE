package com.toasty.domain.seller.entity;

/** 판매자 온보딩 제출값. 스토어 정보와 대표자 정보, 정산 계좌를 한 번에 담는다. */
public record SellerOnboardingCommand(
        Long userId,
        String shopName,
        String description,
        String shopImageObjectKey,
        String sellerName,
        String phoneNumber,
        String businessNumber,
        Bank bank,
        String accountNumber) {}
