package com.toasty.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code imagePrefix}는 셀러 샵 이미지가 머무는 경로다. 샵 이미지는 셀러당 한 장이라 임시 경로를 거치지 않고 처음부터 이 경로에 올린다. 수명주기 규칙이
 * 건드리지 않는다.
 *
 * <p>{@code publicBaseUrl}은 저장된 사진을 브라우저가 읽는 주소다. 나중에 CDN을 붙이면 이 값만 바꾸면 된다.
 *
 * <p>리전은 {@code S3Presigner} 빈을 만들 때만 쓰므로 여기 두지 않는다.
 */
@ConfigurationProperties(prefix = "aws.seller-s3")
public record SellerS3Properties(
        String bucket, String imagePrefix, int presignedUrlExpirySeconds, String publicBaseUrl) {}
