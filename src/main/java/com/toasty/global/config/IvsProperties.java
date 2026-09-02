package com.toasty.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.ivs")
public record IvsProperties(String channelType) {}
