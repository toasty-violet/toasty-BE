package com.toasty.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// JWT secret/만료시간 설정
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret, long accessTokenExpirationMillis, long refreshTokenExpirationMillis) {}
