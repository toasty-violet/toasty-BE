package com.toasty.domain.seller.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 구매자가 결제 전에 보는 배송비. 도서 산간 추가 배송비는 아직 붙이지 않아 내보내지 않는다. */
public record StoreShippingFeeResponse(
        @Schema(description = "기본 배송비(원)", example = "3000") int baseShippingFee,
        @Schema(description = "이 금액 이상 사면 배송비를 받지 않는다. 0이면 무료배송 기준이 없다", example = "50000")
                int freeShippingThreshold) {}
