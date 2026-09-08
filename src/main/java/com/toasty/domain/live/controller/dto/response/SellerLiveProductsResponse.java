package com.toasty.domain.live.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toasty.domain.product.controller.dto.response.LiveProductResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 방송 화면의 전체 상품 시트를 채운다. */
// 고정된 적이 없으면 currentPinnedProductId가 null인데, 그때도 키를 남겨야 화면이 빈 상태를 그린다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SellerLiveProductsResponse(
        @Schema(description = "지금 소개 중인 상품. 아직 아무것도 고정하지 않았으면 null") Long currentPinnedProductId,
        @Schema(description = "편성된 상품 전체. 노출 순서대로") List<LiveProductResponse> products) {}
