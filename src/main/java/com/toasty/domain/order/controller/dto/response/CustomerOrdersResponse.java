package com.toasty.domain.order.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 구매자 주문내역 한 묶음. */
// counts와 nextCursor는 비어 있을 수 있는데, 그때도 화면이 키를 보고 판단한다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CustomerOrdersResponse(
        @Schema(description = "상태 칩 건수. 첫 요청에만 채워지고 이어 받을 때는 null") OrderCountsResponse counts,
        List<CustomerOrderResponse> items,
        @Schema(description = "이어 받을 때 그대로 넘길 값. 더 없으면 null") Long nextCursor,
        @Schema(description = "이어 받을 것이 남았는지") boolean hasNext) {}
