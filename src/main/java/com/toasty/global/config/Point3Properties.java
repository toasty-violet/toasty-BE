package com.toasty.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** point3 결제 API 접속 정보 */
// 승인·취소는 주문 요청이 동기로 기다리므로 타임아웃을 반드시 건다.
@ConfigurationProperties(prefix = "point3")
public record Point3Properties(
        String baseUrl, String apiToken, Duration connectTimeout, Duration readTimeout) {}
