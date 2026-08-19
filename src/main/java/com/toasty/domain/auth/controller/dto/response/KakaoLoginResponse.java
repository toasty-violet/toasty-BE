package com.toasty.domain.auth.controller.dto.response;

import com.toasty.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record KakaoLoginResponse(
        @Schema(description = "유저 ID", example = "1") Long userId,
        @Schema(description = "카카오 고유 ID", example = "3812345678") String kakaoId,
        @Schema(description = "온보딩 완료 여부") boolean isOnboardingCompleted) {

    public static KakaoLoginResponse of(User user) {
        return new KakaoLoginResponse(
                user.getId(), user.getKakaoId(), user.isOnboardingCompleted());
    }
}
