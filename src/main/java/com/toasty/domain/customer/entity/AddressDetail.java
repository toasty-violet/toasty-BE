package com.toasty.domain.customer.entity;

/** 배송지 한 건의 값. 카카오 우편번호 서비스가 내려준 값과 유저가 입력한 상세주소를 담는다. 온보딩·내 정보 수정·내 정보 조회가 함께 쓴다. */
public record AddressDetail(
        String postalCode,
        String roadAddress,
        String jibunAddress,
        AddressType addressType,
        String buildingName,
        String legalDong,
        String detailAddress) {}
