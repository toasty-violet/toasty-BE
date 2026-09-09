package com.toasty.domain.customer.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.customer.entity.AddressDetail;
import com.toasty.domain.customer.entity.AddressType;
import com.toasty.domain.customer.entity.CustomerProfile;
import io.swagger.v3.oas.annotations.media.Schema;

// 값이 없는 자리를 프론트가 그려야 해서, null이어도 키를 남긴다.
// 전역 설정이 non_null이라 그대로 두면 키가 통째로 빠진다.
@JsonInclude(JsonInclude.Include.ALWAYS)
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
                AddressResponse.from(profile.address()));
    }

    // 중첩 레코드는 바깥 설정을 물려받지 않아 여기에도 붙인다.
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AddressResponse(
            @Schema(description = "우편번호 (zonecode)", example = "06236") String postalCode,
            @Schema(
                            description = "도로명 주소 (roadAddress) — 지번만 있는 주소면 null",
                            example = "서울 강남구 테헤란로 152")
                    String roadAddress,
            @Schema(
                            description = "지번 주소 (jibunAddress) — 도로명만 있는 주소면 null",
                            example = "서울 강남구 역삼동 737")
                    String jibunAddress,
            @Schema(description = "유저가 선택한 주소 종류 (userSelectedType) — R은 도로명, J는 지번", example = "R")
                    AddressType addressType,
            @Schema(description = "건물명 (buildingName)", example = "강남파이낸스센터") String buildingName,
            @Schema(description = "법정동·법정리 이름 (bname)", example = "역삼동") String legalDong,
            @Schema(description = "유저가 직접 입력한 상세주소", example = "10층 1001호") String detailAddress) {

        public static AddressResponse from(AddressDetail detail) {
            return new AddressResponse(
                    detail.postalCode(),
                    detail.roadAddress(),
                    detail.jibunAddress(),
                    detail.addressType(),
                    detail.buildingName(),
                    detail.legalDong(),
                    detail.detailAddress());
        }
    }
}
