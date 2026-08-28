package com.toasty.domain.auth.entity;

// 저장소에 보관 중인 리프레시 토큰.
public record RefreshToken(String token, Long userId) {}
