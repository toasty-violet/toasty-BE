package com.toasty.domain.user.controller.dto.response;

import com.toasty.domain.user.entity.Role;
import com.toasty.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserMeResponse(
        @Schema(description = "유저 역할 — 온보딩 전까지 null") Role role,
        @Schema(description = "닉네임 — 온보딩 전이면 가입 시 발급된 임시 닉네임", example = "user_a3f9c2e81b04")
                String nickname,
        @Schema(description = "온보딩 완료 여부") boolean isOnboardingCompleted) {

    // User로 응답 생성
    public static UserMeResponse from(User user) {
        return new UserMeResponse(user.getRole(), user.getNickname(), user.isOnboardingCompleted());
    }
}
