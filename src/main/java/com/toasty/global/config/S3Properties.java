package com.toasty.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code pendingPrefix}는 라이브 저장 전에 올라온 사진이 머무는 경로다. 버킷 수명주기 규칙이 이 경로만 정리하므로 값을 바꾸면 규칙도 함께 바꿔야 한다.
 */
@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(String bucket, String pendingPrefix, int presignedUrlExpirySeconds) {}
