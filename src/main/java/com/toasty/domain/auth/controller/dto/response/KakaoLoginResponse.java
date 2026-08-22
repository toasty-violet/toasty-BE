package com.toasty.domain.auth.controller.dto.response;

import com.toasty.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record KakaoLoginResponse(
        @Schema(description = "온보딩 완료 여부") boolean isOnboardingCompleted,
        @Schema(description = "액세스 토큰") String accessToken) {

    // User + 액세스 토큰으로 응답 생성
    public static KakaoLoginResponse of(User user, String accessToken) {
        return new KakaoLoginResponse(user.isOnboardingCompleted(), accessToken);
    }
}
