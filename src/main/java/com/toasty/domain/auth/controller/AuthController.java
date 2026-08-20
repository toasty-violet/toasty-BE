package com.toasty.domain.auth.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "카카오 로그인 연동 API")
@RestController
@RequestMapping("/api/v1/login")
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
    @GetMapping("/kakao")
    public ApiResponse<KakaoLoginResponse> loginWithKakao(
            @Parameter(description = "카카오 인가 코드", required = true, example = "abc123") @RequestParam
                    String code,
            HttpServletResponse httpResponse) {
        LoginResult result = authService.loginWithKakao(code);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result).toString());
        return ApiResponse.ok(result.response());
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
}
