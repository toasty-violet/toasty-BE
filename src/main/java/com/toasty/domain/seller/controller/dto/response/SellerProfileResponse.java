package com.toasty.domain.seller.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** 화면에 셀러를 표시할 때 필요한 최소 정보. */
// 스토어 이름이나 대표 이미지가 비어도 화면이 자리를 잡아야 해서, null이어도 키를 남긴다.
// 전역 설정이 non_null이라 그대로 두면 키가 통째로 빠진다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SellerProfileResponse(
        @Schema(description = "셀러 번호") Long sellerId,
        @Schema(description = "스토어 이름") String shopName,
        @Schema(description = "스토어 대표 이미지 주소") String shopImageUrl) {}
