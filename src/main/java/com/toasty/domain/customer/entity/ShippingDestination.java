package com.toasty.domain.customer.entity;

/** 주문에 복사할 받는사람과 기본 배송지. 온보딩에서 모두 필수로 받으므로 빈 값이 없다. */
public record ShippingDestination(
        String receiverName,
        String receiverPhone,
        String postalCode,
        String address,
        String detailAddress) {}
