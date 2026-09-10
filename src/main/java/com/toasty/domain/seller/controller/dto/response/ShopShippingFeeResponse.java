package com.toasty.domain.seller.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 스토어 관리 화면의 배송비 정책. */
public record ShopShippingFeeResponse(
        @Schema(description = "기본 배송비(원)", example = "3000") int baseShippingFee,
        @Schema(description = "무료배송 기준 금액(원). 0이면 무료배송 기준이 없다", example = "50000")
                int freeShippingThreshold,
        @Schema(description = "도서 산간 배송비(원). 기본 배송비에 더해 받는다", example = "3000")
                int remoteAreaShippingFee) {}
