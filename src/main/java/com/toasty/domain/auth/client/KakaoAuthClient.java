package com.toasty.domain.auth.client;

import com.toasty.domain.auth.client.dto.KakaoTokenResponse;
import com.toasty.domain.auth.client.dto.KakaoUserResponse;
import com.toasty.domain.auth.exception.AuthErrorCode;
import com.toasty.global.config.KakaoProperties;
import com.toasty.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class KakaoAuthClient {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final KakaoProperties kakaoProperties;
    private final RestClient restClient = RestClient.create();

    public KakaoTokenResponse requestToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", kakaoProperties.clientId());
        body.add("redirect_uri", kakaoProperties.redirectUri());
        body.add("code", code);
        if (kakaoProperties.clientSecret() != null && !kakaoProperties.clientSecret().isBlank()) {
            body.add("client_secret", kakaoProperties.clientSecret());
        }

        try {
            return restClient
                    .post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
        } catch (Exception e) {
            throw new CustomException(AuthErrorCode.KAKAO_TOKEN_REQUEST_FAILED, e);
        }
    }

    public KakaoUserResponse requestUserInfo(String accessToken) {
        try {
            return restClient
                    .get()
                    .uri(USER_INFO_URI)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
        } catch (Exception e) {
            throw new CustomException(AuthErrorCode.KAKAO_USER_INFO_REQUEST_FAILED, e);
        }
    }
}
