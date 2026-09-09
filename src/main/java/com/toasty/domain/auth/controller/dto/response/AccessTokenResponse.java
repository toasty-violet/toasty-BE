package com.toasty.domain.auth.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AccessTokenResponse(@Schema(description = "발급된 액세스 토큰") String accessToken) {}
