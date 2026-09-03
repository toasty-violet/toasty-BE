package com.toasty.domain.auth.entity;

import com.toasty.domain.user.entity.Role;

// 액세스 토큰에서 추출한 userId로 조회해 유저 정보와 역할을 반환한다.
public record AuthUser(Long userId, Role role) {}
