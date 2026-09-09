package com.toasty.domain.customer.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record NicknameSuggestionResponse(
        @Schema(description = "지금은 아무도 쓰고 있지 않은 닉네임", example = "바삭한식빵482") String nickname) {}
