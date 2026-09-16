package com.toasty.global.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

// 리프레시 토큰 만료시간 설정. Redis TTL과 쿠키 maxAge에 이 값을 쓴다
// rotationGrace는 방금 교체된 토큰이 다시 와도 유출로 보지 않고 받아주는 시간이다
@ConfigurationProperties(prefix = "auth.refresh-token")
public record RefreshTokenProperties(
        @DurationUnit(ChronoUnit.MILLIS) Duration expiration,
        @DurationUnit(ChronoUnit.MILLIS) Duration rotationGrace) {}
