package com.toasty.domain.seller.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 스토어 관리 화면의 판매 내역. */
public record ShopSalesSummaryResponse(
        @Schema(description = "총 판매 수", example = "0") int totalSalesCount,
        @Schema(description = "누적 구매자 수", example = "0") int totalBuyerCount,
        @Schema(description = "누적 판매액(원)", example = "0") long totalSalesAmount) {}
