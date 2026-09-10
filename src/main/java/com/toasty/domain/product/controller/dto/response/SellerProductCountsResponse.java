package com.toasty.domain.product.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 상품탭 상태 칩에 붙는 건수. */
public record SellerProductCountsResponse(
        @Schema(description = "판매중 + 라이브 예정") int all,
        @Schema(description = "판매중") int onSale,
        @Schema(description = "라이브 예정") int scheduled) {}
