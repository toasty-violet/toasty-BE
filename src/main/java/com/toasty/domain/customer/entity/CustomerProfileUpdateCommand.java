package com.toasty.domain.customer.entity;

/** 구매자 내 정보 수정 제출값. 구매자 정보와 기본 배송지를 한 번에 담는다. */
public record CustomerProfileUpdateCommand(
        Long customerId, String name, String nickname, String phoneNumber, AddressDetail address) {}
