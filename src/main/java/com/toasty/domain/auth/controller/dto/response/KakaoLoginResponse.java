package com.toasty.domain.auth.controller.dto.response;

import com.toasty.domain.user.entity.User;

public record KakaoLoginResponse(Long userId, String kakaoId, boolean isNewUser) {

    public static KakaoLoginResponse of(User user, boolean isNewUser) {
        return new KakaoLoginResponse(user.getId(), user.getKakaoId(), isNewUser);
    }
}
