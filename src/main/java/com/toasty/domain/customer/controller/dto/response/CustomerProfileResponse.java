package com.toasty.domain.customer.controller.dto.response;

import com.toasty.domain.customer.entity.CustomerProfile;
import io.swagger.v3.oas.annotations.media.Schema;

/** 구매자 내 정보 화면 한 장을 채운다. */
public record CustomerProfileResponse(
        @Schema(description = "이름", example = "홍길동") String name,
        @Schema(description = "닉네임", example = "토스티") String nickname,
        @Schema(description = "휴대폰 번호. 하이픈 없이 저장된 값 그대로", example = "01012345678")
                String phoneNumber,
        @Schema(description = "기본 배송지") AddressResponse address) {

    public static CustomerProfileResponse of(String nickname, CustomerProfile profile) {
        return new CustomerProfileResponse(
                profile.name(),
                nickname,
                profile.phoneNumber(),
                new AddressResponse(
                        profile.postalCode(), profile.address(), profile.detailAddress()));
    }

    public record AddressResponse(
            @Schema(description = "우편번호", example = "01234") String postalCode,
            @Schema(description = "유저가 선택한 종류의 주소 (도로명 또는 지번)", example = "서울특별시 강남구 테헤란로 123")
                    String address,
            @Schema(description = "유저가 직접 입력한 상세주소", example = "101동 101호") String detailAddress) {}
}
