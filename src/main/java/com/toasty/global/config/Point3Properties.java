package com.toasty.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** point3 결제 API 접속 정보 */
@ConfigurationProperties(prefix = "point3")
public record Point3Properties(String baseUrl, String apiToken) {}
