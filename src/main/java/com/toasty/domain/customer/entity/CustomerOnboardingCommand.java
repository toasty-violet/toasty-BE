package com.toasty.domain.customer.entity;

/** 구매자 온보딩 제출값. 구매자 정보와 기본 배송지, 계좌 등록 결제 세션을 한 번에 담는다. */
public record CustomerOnboardingCommand(
        Long userId,
        String name,
        String nickname,
        String phoneNumber,
        String sessionId,
        AddressDetail address) {}
