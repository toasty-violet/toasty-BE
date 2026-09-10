package com.toasty.domain.seller.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ShopNameSuggestionResponse(
        @Schema(description = "지금은 아무도 쓰고 있지 않은 스토어 이름", example = "골목빵집482") String shopName) {}
