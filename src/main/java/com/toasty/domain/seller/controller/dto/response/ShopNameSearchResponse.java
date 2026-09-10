package com.toasty.domain.seller.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ShopNameSearchResponse(
        @Schema(description = "다른 판매자가 이미 쓰고 있는 스토어 이름이면 true", example = "false")
                boolean duplicated) {}
