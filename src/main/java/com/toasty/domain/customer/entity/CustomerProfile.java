package com.toasty.domain.customer.entity;

/** 구매자 정보와 기본 배송지 조회 결과. 닉네임은 users에 있어 여기 담지 않는다. */
public record CustomerProfile(String name, String phoneNumber, AddressDetail address) {}
