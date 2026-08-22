package com.toasty.domain.auth.controller;

import com.toasty.domain.auth.controller.dto.response.AccessTokenResponse;
import com.toasty.domain.auth.controller.dto.response.KakaoLoginResponse;
import com.toasty.domain.auth.service.AuthService;
import com.toasty.domain.auth.service.LoginResult;
import com.toasty.global.exception.ErrorResponse;
import com.toasty.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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
                    "인가 코드로 로그인/가입 처리 후 액세스 토큰과 리프레시 토큰을 발급한다. 신규 회원이면 자동 가입 후 응답 반환. "
                            + "액세스 토큰은 응답 본문으로, 리프레시 토큰은 HttpOnly 쿠키로 내려간다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "로그인/가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "502",
                description = "카카오 토큰 발급 실패 또는 사용자 정보 조회 실패",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
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
    public ApiResponse<KakaoLoginResponse> loginWithKakao(
            @Parameter(description = "카카오 인가 코드", required = true, example = "abc123") @RequestParam
                    String code,
            HttpServletResponse httpResponse) {
        LoginResult result = authService.loginWithKakao(code);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result).toString());
        return ApiResponse.ok(result.response());
    }

    @Operation(
            summary = "액세스 토큰 재발급",
            description = "쿠키의 리프레시 토큰이 유효하면 새 액세스 토큰을 발급한다. 리프레시 토큰 자체는 갱신하지 않는다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "재발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "리프레시 토큰이 없거나, 위조/만료되었거나, 이미 로그아웃된 상태",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    // 로그인이 계속 유지되도록, 만료된 액세스 토큰만 새 것으로 바꿔준다
    @PostMapping("/refresh")
    public ApiResponse<AccessTokenResponse> refresh(
            @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
        String accessToken = authService.reissueAccessToken(refreshToken);
        return ApiResponse.ok(new AccessTokenResponse(accessToken));
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
    private ResponseCookie buildRefreshTokenCookie(LoginResult result) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, result.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(result.refreshTokenMaxAge())
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
