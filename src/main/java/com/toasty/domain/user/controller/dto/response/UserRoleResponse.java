package com.toasty.domain.user.controller.dto.response;

import com.toasty.domain.user.entity.Role;
import com.toasty.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserRoleResponse(@Schema(description = "유저 역할 — 온보딩 전에는 응답에서 생략된다") Role role) {

    // User로 응답 생성
    public static UserRoleResponse from(User user) {
        return new UserRoleResponse(user.getRole());
    }
}
