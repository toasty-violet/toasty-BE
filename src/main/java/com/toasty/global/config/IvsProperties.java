package com.toasty.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** IVS는 7개 리전에서만 제공된다. 미지원 리전을 넣으면 채널 생성이 실패한다. */
@ConfigurationProperties(prefix = "aws.ivs")
public record IvsProperties(String region, String channelType, String latencyMode) {}
