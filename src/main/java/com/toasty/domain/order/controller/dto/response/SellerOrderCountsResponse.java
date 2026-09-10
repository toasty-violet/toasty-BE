package com.toasty.domain.order.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 주문탭 상태 칩에 붙는 건수. */
public record SellerOrderCountsResponse(
        @Schema(description = "배송대기 + 발송완료") int all,
        @Schema(description = "배송대기") int shippingPending,
        @Schema(description = "발송완료") int shipped) {}
