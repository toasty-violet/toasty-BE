package com.toasty.domain.auth.service;

import com.toasty.domain.auth.client.KakaoAuthClient;
import com.toasty.domain.auth.client.dto.KakaoTokenResponse;
import com.toasty.domain.auth.client.dto.KakaoUserResponse;
import com.toasty.domain.auth.controller.dto.response.KakaoLoginResponse;
import com.toasty.domain.auth.exception.AuthErrorCode;
import com.toasty.domain.auth.repository.RefreshTokenRepository;
import com.toasty.domain.auth.token.JwtTokenProvider;
import com.toasty.domain.user.entity.User;
import com.toasty.domain.user.service.UserLoginResult;
import com.toasty.domain.user.service.UserService;
import com.toasty.global.exception.CustomException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoAuthClient kakaoAuthClient;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    // 카카오 로그인 처리 후 JWT 발급 및 리프레시 토큰 저장
    public LoginResult loginWithKakao(String code) {
        KakaoTokenResponse token = kakaoAuthClient.requestToken(code);
        KakaoUserResponse kakaoUser = kakaoAuthClient.requestUserInfo(token.accessToken());

        UserLoginResult result = userService.loginWithKakao(String.valueOf(kakaoUser.id()));
        User user = result.user();

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        Duration refreshTokenMaxAge = jwtTokenProvider.getRefreshTokenExpiration();
        refreshTokenRepository.save(user.getId(), refreshToken, refreshTokenMaxAge);

        return new LoginResult(
                KakaoLoginResponse.of(user, accessToken), refreshToken, refreshTokenMaxAge);
    }

    // 리프레시 토큰이 진짜인지, 로그아웃되지 않았는지 확인하고 새 액세스 토큰을 발급한다
    public String reissueAccessToken(String refreshToken) {
        if (refreshToken == null) {
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        Long userId;
        try {
            userId = jwtTokenProvider.parseUserId(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_INVALID, e);
        }

        String savedRefreshToken =
                refreshTokenRepository
                        .findByUserId(userId)
                        .orElseThrow(
                                () -> new CustomException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));
        if (!savedRefreshToken.equals(refreshToken)) {
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        return jwtTokenProvider.generateAccessToken(userId);
    }

    // 로그아웃 — 리프레시 토큰에서 알아낸 사용자의 로그인 상태를 지운다. 토큰이 없거나 이미 못 쓰는 토큰이어도 로그아웃 자체는 실패시키지 않는다
    public void logout(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        jwtTokenProvider
                .parseUserIdIgnoringExpiration(refreshToken)
                .ifPresent(refreshTokenRepository::deleteByUserId);
    }
}
