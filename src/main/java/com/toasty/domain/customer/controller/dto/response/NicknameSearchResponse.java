package com.toasty.domain.customer.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record NicknameSearchResponse(
        @Schema(description = "다른 구매자가 이미 쓰고 있는 닉네임이면 true", example = "false")
                boolean duplicated) {}
