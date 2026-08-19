package com.toasty.domain.auth.service;

import com.toasty.domain.auth.client.KakaoAuthClient;
import com.toasty.domain.auth.client.dto.KakaoTokenResponse;
import com.toasty.domain.auth.client.dto.KakaoUserResponse;
import com.toasty.domain.auth.controller.dto.response.KakaoLoginResponse;
import com.toasty.domain.user.service.UserLoginResult;
import com.toasty.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoAuthClient kakaoAuthClient;
    private final UserService userService;

    public KakaoLoginResponse loginWithKakao(String code) {
        KakaoTokenResponse token = kakaoAuthClient.requestToken(code);
        KakaoUserResponse kakaoUser = kakaoAuthClient.requestUserInfo(token.accessToken());

        UserLoginResult result = userService.loginWithKakao(String.valueOf(kakaoUser.id()));
        return KakaoLoginResponse.of(result.user());
    }
}
