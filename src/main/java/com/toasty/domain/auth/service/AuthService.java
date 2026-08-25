package com.toasty.domain.auth.service;

import com.toasty.domain.auth.client.KakaoAuthClient;
import com.toasty.domain.auth.client.dto.KakaoTokenResponse;
import com.toasty.domain.auth.client.dto.KakaoUserResponse;
import com.toasty.domain.auth.controller.dto.response.KakaoLoginResponse;
import com.toasty.domain.auth.entity.RefreshToken;
import com.toasty.domain.auth.exception.AuthErrorCode;
import com.toasty.domain.auth.repository.RefreshTokenRepository;
import com.toasty.domain.auth.token.JwtTokenProvider;
import com.toasty.domain.auth.token.RefreshTokenGenerator;
import com.toasty.domain.user.entity.User;
import com.toasty.domain.user.service.UserLoginResult;
import com.toasty.domain.user.service.UserService;
import com.toasty.global.config.RefreshTokenProperties;
import com.toasty.global.exception.CustomException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoAuthClient kakaoAuthClient;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenProperties refreshTokenProperties;

    // 카카오 로그인 처리 후 JWT 발급 및 리프레시 토큰 저장
    public LoginResult loginWithKakao(String code) {
        KakaoTokenResponse token = kakaoAuthClient.requestToken(code);
        KakaoUserResponse kakaoUser = kakaoAuthClient.requestUserInfo(token.accessToken());

        UserLoginResult result = userService.loginWithKakao(String.valueOf(kakaoUser.id()));
        User user = result.user();

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = issueRefreshToken(user.getId());

        return new LoginResult(
                KakaoLoginResponse.of(user, accessToken),
                refreshToken,
                refreshTokenProperties.expiration());
    }

    // 액세스 토큰을 재발급하고 리프레시 토큰도 새것으로 교체한다.
    // 넘어온 토큰이 없거나 저장소에 없으면 401, 이미 소비된 토큰이면 그 사용자의 모든 토큰을 지우고 401을 던진다.
    public ReissueResult reissueAccessToken(String refreshToken) {
        if (refreshToken == null) {
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        RefreshToken stored =
                refreshTokenRepository
                        .findByToken(refreshToken)
                        .orElseThrow(
                                () -> new CustomException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

        // 소비된 토큰의 재사용은 유출로 간주하고 이 사용자의 모든 기기를 로그아웃시킨다
        if (stored.used()) {
            refreshTokenRepository.deleteAllByUserId(stored.userId());
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }

        Long userId = stored.userId();
        refreshTokenRepository.markUsed(stored);

        return new ReissueResult(
                jwtTokenProvider.generateAccessToken(userId),
                issueRefreshToken(userId),
                refreshTokenProperties.expiration());
    }

    // 이 기기의 리프레시 토큰만 지운다. 토큰이 없거나 저장소에 없어도 예외 없이 통과시킨다
    public void logout(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
    }

    // 새 리프레시 토큰을 만들어 TTL과 함께 저장하고 그 값을 돌려준다
    private String issueRefreshToken(Long userId) {
        Duration ttl = refreshTokenProperties.expiration();
        String refreshToken = refreshTokenGenerator.generate();
        refreshTokenRepository.save(refreshToken, userId, ttl);
        return refreshToken;
    }
}
