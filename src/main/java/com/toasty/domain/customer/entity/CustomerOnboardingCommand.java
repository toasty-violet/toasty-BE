package com.toasty.domain.customer.entity;

/** 구매자 온보딩 제출값. 구매자 정보와 기본 배송지를 한 번에 담는다. */
public record CustomerOnboardingCommand(
        Long userId, String name, String nickname, String phoneNumber, AddressCommand address) {

    /** 카카오 우편번호 서비스가 내려준 값과 유저가 입력한 상세주소. */
    public record AddressCommand(
            String postalCode,
            String roadAddress,
            String jibunAddress,
            AddressType addressType,
            String buildingName,
            String legalDong,
            String detailAddress) {}
}
