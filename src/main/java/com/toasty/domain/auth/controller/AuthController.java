package com.toasty.domain.auth.controller;

import com.toasty.domain.auth.controller.dto.response.KakaoLoginResponse;
import com.toasty.domain.auth.service.AuthService;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "카카오 로그인 연동 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "카카오 로그인 콜백 — 인가 코드로 로그인/가입 처리")
    @GetMapping("/kakao/callback")
    public ApiResponse<KakaoLoginResponse> kakaoCallback(@RequestParam String code) {
        return ApiResponse.ok(authService.loginWithKakao(code));
    }
}
