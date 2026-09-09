package com.toasty.global.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

// 액세스 토큰 서명키와 만료시간 설정
@ConfigurationProperties(prefix = "auth.access-token")
public record AccessTokenProperties(
        String secret, @DurationUnit(ChronoUnit.MILLIS) Duration expiration) {}
