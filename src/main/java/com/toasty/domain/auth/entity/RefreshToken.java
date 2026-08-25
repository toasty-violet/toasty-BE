package com.toasty.domain.auth.entity;

// 저장소에 보관 중인 리프레시 토큰. used는 회전으로 교체되어 더 이상 재발급에 쓸 수 없는 상태를 뜻한다.
public record RefreshToken(String token, Long userId, boolean used) {}
