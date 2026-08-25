package com.toasty.global.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

// JWT 서명키와 액세스 토큰 만료시간 설정
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret, @DurationUnit(ChronoUnit.MILLIS) Duration accessTokenExpiration) {}
