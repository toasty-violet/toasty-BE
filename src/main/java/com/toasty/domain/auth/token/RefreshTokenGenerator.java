package com.toasty.domain.auth.token;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

// 리프레시 토큰 생성. 값 자체에는 아무 정보도 담기지 않은 난수 문자열(opaque token)이라,
// 유효성은 서명이 아니라 저장소에 남아있는지로 판단한다.
@Component
public class RefreshTokenGenerator {

    // 256비트. Base64 URL-safe로 인코딩해 쿠키에 그대로 담을 수 있게 한다.
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
