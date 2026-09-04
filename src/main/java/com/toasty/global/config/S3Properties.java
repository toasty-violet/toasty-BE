package com.toasty.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code pendingPrefix}는 라이브 저장 전에 올라온 사진이 머무는 경로다. 버킷 수명주기 규칙이 이 경로만 정리하므로 값을 바꾸면 규칙도 함께 바꿔야 한다.
 * {@code imagePrefix}는 상품으로 확정된 사진이 옮겨 가는 경로다. 수명주기 규칙이 건드리지 않는다.
 *
 * <p>{@code sellerImagePrefix}는 셀러 샵 이미지가 머무는 경로다. 샵 이미지는 셀러당 한 장이라 임시 경로를 거치지 않고 처음부터 이 경로에 올린다.
 *
 * <p>{@code publicBaseUrl}은 저장된 사진을 브라우저가 읽는 주소다. 나중에 CDN을 붙이면 이 값만 바꾸면 된다.
 */
@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
        String region,
        String bucket,
        String pendingPrefix,
        String imagePrefix,
        String sellerImagePrefix,
        int presignedUrlExpirySeconds,
        String publicBaseUrl) {}
