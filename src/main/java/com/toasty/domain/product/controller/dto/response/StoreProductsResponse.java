package com.toasty.domain.product.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 스토어 상품 한 묶음. */
// nextCursor는 비어 있을 수 있는데, 그때도 화면이 키를 보고 판단한다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record StoreProductsResponse(
        List<StoreProductResponse> items,
        @Schema(description = "이어 받을 때 그대로 넘길 값. 더 없으면 null") Long nextCursor,
        @Schema(description = "이어 받을 것이 남았는지") boolean hasNext) {}
