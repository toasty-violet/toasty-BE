package com.toasty.domain.seller.entity;

/** 스토어 정보 수정 제출값. 이미지 소유자를 가려내야 해서 유저 번호도 함께 담는다. */
public record ShopUpdateCommand(
        Long userId,
        Long sellerId,
        String shopName,
        String shopImageObjectKey,
        String description,
        int baseShippingFee,
        int freeShippingThreshold,
        int remoteAreaShippingFee) {}
