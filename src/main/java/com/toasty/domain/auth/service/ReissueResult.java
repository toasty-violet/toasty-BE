package com.toasty.domain.auth.service;

import java.time.Duration;

// 재발급 결과 — 새 액세스 토큰과, 쿠키에 다시 심을 리프레시 토큰 및 만료시간
public record ReissueResult(String accessToken, String refreshToken, Duration refreshTokenMaxAge) {}
