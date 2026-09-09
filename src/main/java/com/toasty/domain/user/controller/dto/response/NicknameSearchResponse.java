package com.toasty.domain.user.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record NicknameSearchResponse(
        @Schema(description = "이미 사용 중인 닉네임이면 true", example = "false") boolean duplicated) {}
