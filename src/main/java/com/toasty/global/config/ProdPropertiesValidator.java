package com.toasty.global.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 배포에 필요한 환경변수가 빠졌으면 기동을 세운다. */
// @ConfigurationProperties 바인딩은 해석하지 못한 플레이스홀더를 그대로 값으로 넘긴다(@Value와 다르다).
// 그대로 두면 서버는 멀쩡히 뜨고, 프론트가 CORS로 막히거나 로그인이 KOE006으로 죽는 식으로
// 한참 뒤에야 드러난다. 뜨기 전에 끊는 편이 낫다.
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdPropertiesValidator {

    private final CorsProperties corsProperties;
    private final KakaoProperties kakaoProperties;
    private final S3Properties s3Properties;
    private final AccessTokenProperties accessTokenProperties;

    @PostConstruct
    void validate() {
        requireInjected("CORS_ALLOWED_ORIGINS", corsProperties.allowedOrigins());
        requireInjected("KAKAO_REDIRECT_URI", kakaoProperties.redirectUri());
        requireInjected("KAKAO_CLIENT_ID", kakaoProperties.clientId());
        requireInjected("AWS_S3_BUCKET", s3Properties.bucket());
        requireInjected("AWS_S3_PUBLIC_BASE_URL", s3Properties.publicBaseUrl());
        requireInjected("JWT_SECRET", accessTokenProperties.secret());
    }

    private void requireInjected(String name, List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException(message(name));
        }
        values.forEach(value -> requireInjected(name, value));
    }

    private void requireInjected(String name, String value) {
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalStateException(message(name));
        }
    }

    private String message(String name) {
        return "배포에 필요한 환경변수 " + name + "이(가) 주입되지 않았습니다. 값을 넣고 다시 띄우세요.";
    }
}
