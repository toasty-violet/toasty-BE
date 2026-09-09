package com.toasty.domain.auth.service;

import java.time.Duration;

// 로그인 처리 결과 — 액세스 토큰 + 쿠키 설정에 필요한 리프레시 토큰 값
public record LoginResult(String accessToken, String refreshToken, Duration refreshTokenMaxAge) {}
