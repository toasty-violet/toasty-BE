package com.toasty.domain.auth.service;

import com.toasty.domain.auth.controller.dto.response.KakaoLoginResponse;
import java.time.Duration;

// 로그인 처리 결과 — 응답 DTO + 쿠키 설정에 필요한 리프레시 토큰 값
public record LoginResult(
        KakaoLoginResponse response, String refreshToken, Duration refreshTokenMaxAge) {}
