package com.toasty.domain.customer.entity;

/** 구매자 온보딩 제출값. 구매자 정보와 기본 배송지를 한 번에 담는다. */
public record CustomerOnboardingCommand(
        Long userId, String name, String nickname, String phoneNumber, AddressDetail address) {}
