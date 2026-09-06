package com.toasty.domain.auth.controller;

import com.toasty.domain.auth.controller.dto.response.AccessTokenResponse;
import com.toasty.domain.auth.service.AuthService;
import com.toasty.domain.auth.service.LoginResult;
import com.toasty.domain.auth.service.ReissueResult;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "유저 인증, 토큰 관리 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final AuthService authService;

    @Operation(
            summary = "카카오 로그인",
            description =
                    "카카오 인증으로 로그인합니다. 액세스 토큰은 응답 본문으로, 리프레시 토큰은 HttpOnly 쿠키로 받습니다. "
                            + "유저 정보는 GET /api/v1/users/me로 조회하세요.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "로그인/가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "502",
                description = "카카오 토큰 발급 실패 또는 사용자 정보 조회 실패",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples = {
                                    @ExampleObject(
                                            name = "카카오 토큰 발급 실패",
                                            value =
                                                    """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "AUTH_KAKAO_TOKEN_REQUEST_FAILED",
                                                        "message": "카카오 토큰 발급에 실패했습니다."
                                                      }
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "카카오 사용자 정보 조회 실패",
                                            value =
                                                    """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "AUTH_KAKAO_USER_INFO_REQUEST_FAILED",
                                                        "message": "카카오 사용자 정보 조회에 실패했습니다."
                                                      }
                                                    }
                                                    """)
                                }))
    })
    // 카카오 로그인 — 액세스 토큰은 응답 본문, 리프레시 토큰은 HttpOnly 쿠키로 반환
    @GetMapping("/login/kakao")
    public ApiResponse<AccessTokenResponse> loginWithKakao(
            @Parameter(description = "카카오 인가 코드", required = true, example = "abc123") @RequestParam
                    String code,
            HttpServletResponse httpResponse) {
        LoginResult result = authService.loginWithKakao(code);
        httpResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                buildRefreshTokenCookie(result.refreshToken(), result.refreshTokenMaxAge())
                        .toString());
        return ApiResponse.ok(new AccessTokenResponse(result.accessToken()));
    }

    @Operation(
            summary = "액세스 토큰 재발급",
            description =
                    "쿠키의 리프레시 토큰으로 새 액세스 토큰을 발급하고, 리프레시 토큰도 새것으로 교체해 쿠키에 다시 내려준다. "
                            + "교체된 이전 토큰으로 다시 요청하면 모든 기기에서 로그아웃되므로, 재발급은 한 번에 한 요청만 보내야 한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "재발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "리프레시 토큰이 없거나, 만료/로그아웃되었거나, 이미 교체된 토큰으로 요청한 경우",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples = {
                                    @ExampleObject(
                                            name = "AUTH_REFRESH_TOKEN_NOT_FOUND",
                                            description = "쿠키에 리프레시 토큰이 없거나 만료/로그아웃된 경우",
                                            value =
                                                    """
                                                    {"success": false, "error": {"code": "AUTH_REFRESH_TOKEN_NOT_FOUND", "message": "만료되었거나 존재하지 않는 리프레시 토큰입니다."}}
                                                    """),
                                    @ExampleObject(
                                            name = "AUTH_REFRESH_TOKEN_REUSE_DETECTED",
                                            description = "이미 교체된 리프레시 토큰으로 다시 요청한 경우",
                                            value =
                                                    """
                                                    {"success": false, "error": {"code": "AUTH_REFRESH_TOKEN_REUSE_DETECTED", "message": "이미 사용된 리프레시 토큰입니다. 안전을 위해 모든 기기에서 로그아웃되었으니 다시 로그인해 주세요."}}
                                                    """)
                                }))
    })
    // 액세스 토큰을 재발급하고, 교체된 리프레시 토큰을 쿠키에 다시 심는다
    @PostMapping("/refresh")
    public ApiResponse<AccessTokenResponse> refresh(
            @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse httpResponse) {
        ReissueResult result = authService.reissueAccessToken(refreshToken);
        httpResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                buildRefreshTokenCookie(result.refreshToken(), result.refreshTokenMaxAge())
                        .toString());
        return ApiResponse.ok(new AccessTokenResponse(result.accessToken()));
    }

    @Operation(summary = "로그아웃", description = "서버에 저장된 로그인 상태를 지우고, 브라우저의 리프레시 토큰 쿠키도 만료시킨다.")
    // 로그아웃 — 서버의 로그인 기록을 지우고, 브라우저 쪽 리프레시 토큰 쿠키도 즉시 만료시킨다
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse httpResponse) {
        authService.logout(refreshToken);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, buildExpiredRefreshTokenCookie().toString());
        return ApiResponse.ok();
    }

    // 리프레시 토큰을 담은 HttpOnly 쿠키 생성
    private ResponseCookie buildRefreshTokenCookie(String refreshToken, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    // 로그아웃 시 브라우저에서 리프레시 토큰 쿠키를 바로 지우기 위한 만료 쿠키 생성
    private ResponseCookie buildExpiredRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();
    }
}
