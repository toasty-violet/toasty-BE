package com.toasty.domain.auth.controller.dto.response;

import com.toasty.domain.user.entity.User;

public record KakaoLoginResponse(Long userId, String kakaoId, boolean isOnboardingCompleted) {

    public static KakaoLoginResponse of(User user) {
        return new KakaoLoginResponse(
                user.getId(), user.getKakaoId(), user.isOnboardingCompleted());
    }
}
