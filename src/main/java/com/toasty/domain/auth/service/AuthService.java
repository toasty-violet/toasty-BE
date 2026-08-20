package com.toasty.domain.auth.service;

import com.toasty.domain.auth.client.KakaoAuthClient;
import com.toasty.domain.auth.client.dto.KakaoTokenResponse;
import com.toasty.domain.auth.client.dto.KakaoUserResponse;
import com.toasty.domain.auth.controller.dto.response.KakaoLoginResponse;
import com.toasty.domain.auth.repository.RefreshTokenRepository;
import com.toasty.domain.auth.token.JwtTokenProvider;
import com.toasty.domain.user.entity.User;
import com.toasty.domain.user.service.UserLoginResult;
import com.toasty.domain.user.service.UserService;
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
}
