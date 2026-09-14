package com.toasty.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Solapi 문자 발송 접속 정보 */
@ConfigurationProperties(prefix = "solapi")
public record SolapiProperties(
        String baseUrl, String apiKey, String apiSecret, String senderNumber) {

    /** 로컬처럼 키를 넣지 않은 환경이면 문자를 실제로 보내지 않는다. */
    public boolean isConfigured() {
        return hasText(apiKey) && hasText(apiSecret) && hasText(senderNumber);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
