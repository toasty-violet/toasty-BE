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
    private static final String UNLINK_URI = "https://kapi.kakao.com/v1/user/unlink";

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

    /** 탈퇴한 회원과 우리 앱의 카카오 연결을 끊는다. 다시 로그인하면 동의 화면부터 시작한다. */
    // 유저의 카카오 액세스 토큰은 로그인 직후 버려서 탈퇴 시점에는 없다. 회원번호만으로 부를 수 있는 어드민 키를 쓴다.
    public void unlink(String kakaoId) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("target_id_type", "user_id");
        body.add("target_id", kakaoId);

        try {
            restClient
                    .post()
                    .uri(UNLINK_URI)
                    .header("Authorization", "KakaoAK " + kakaoProperties.adminKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new CustomException(AuthErrorCode.KAKAO_UNLINK_FAILED, e);
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
