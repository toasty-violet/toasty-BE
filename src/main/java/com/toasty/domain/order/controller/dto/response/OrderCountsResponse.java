package com.toasty.domain.order.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 주문 화면 상태 칩에 붙는 건수. 셀러 주문탭과 구매자 주문내역이 같이 쓴다. */
public record OrderCountsResponse(
        @Schema(description = "배송대기 + 발송완료") int all,
        @Schema(description = "배송대기") int shippingPending,
        @Schema(description = "발송완료") int shipped) {}
