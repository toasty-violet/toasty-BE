package com.toasty.domain.customer.controller.dto.request;

import com.toasty.domain.customer.entity.AddressDetail;
import com.toasty.domain.customer.entity.AddressType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 카카오 우편번호 서비스 응답을 그대로 담고, 상세주소만 유저가 입력한다. 온보딩과 내 정보 수정이 함께 쓴다.
public record AddressRequest(
        @Schema(description = "우편번호 (zonecode)", example = "06236")
                @NotBlank(message = "우편번호는 필수입니다.") @Size(max = 10, message = "우편번호는 10자를 넘을 수 없습니다.") String postalCode,
        @Schema(description = "도로명 주소 (roadAddress) — 지번만 있는 주소면 null", example = "서울 강남구 테헤란로 152")
                @Size(max = 255, message = "도로명 주소는 255자를 넘을 수 없습니다.") String roadAddress,
        @Schema(description = "지번 주소 (jibunAddress) — 도로명만 있는 주소면 null", example = "서울 강남구 역삼동 737")
                @Size(max = 255, message = "지번 주소는 255자를 넘을 수 없습니다.") String jibunAddress,
        @Schema(description = "유저가 선택한 주소 종류 (userSelectedType) — R은 도로명, J는 지번", example = "R")
                @NotNull(message = "주소 종류는 필수입니다.") AddressType addressType,
        @Schema(description = "건물명 (buildingName)", example = "강남파이낸스센터")
                @Size(max = 100, message = "건물명은 100자를 넘을 수 없습니다.") String buildingName,
        @Schema(description = "법정동·법정리 이름 (bname)", example = "역삼동")
                @Size(max = 50, message = "법정동은 50자를 넘을 수 없습니다.") String legalDong,
        @Schema(description = "상세주소", example = "10층 1001호")
                @Size(max = 255, message = "상세주소는 255자를 넘을 수 없습니다.") String detailAddress) {

    public AddressDetail toCommand() {
        return new AddressDetail(
                postalCode,
                roadAddress,
                jibunAddress,
                addressType,
                buildingName,
                legalDong,
                detailAddress);
    }
}
