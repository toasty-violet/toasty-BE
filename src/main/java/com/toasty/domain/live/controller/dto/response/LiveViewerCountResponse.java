package com.toasty.domain.live.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record LiveViewerCountResponse(
        @Schema(description = "지금 보고 있는 사람 수. 방송 중이 아니면 0") int viewerCount,
        @Schema(description = "판매율(%). 이 방송에서 팔린 수량 / (남은 재고 + 팔린 수량)") int sellThroughRate) {}
