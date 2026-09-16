package com.toasty.domain.live.client;

import com.toasty.global.config.S3Properties;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

/** 방송 중인 화면을 IVS가 S3에 적어 둔 최신 썸네일을 찾는다. */
// IVS는 방송 세션마다 새 경로에 기록하고, 그 안의 최신 썸네일 한 장만 계속 덮어쓴다.
// 그래서 방송이 시작된 뒤 세션 경로를 한 번 찾아 두면 그 주소가 방송 내내 최신 화면을 가리킨다.
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveThumbnailClient {

    private static final String RECORDING_PREFIX = "ivs/v1/";
    private static final String LATEST_THUMBNAIL_SUFFIX = "/media/latest_thumbnail/thumb.jpg";

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    /** 이 채널이 지금 방송에서 쓰는 썸네일 주소. 아직 한 장도 찍히지 않았으면 비어 있다. */
    // 첫 썸네일은 IVS가 정한 간격이 지나야 생겨 방송 직후에는 없을 수 있다.
    public Optional<String> findLatestThumbnailUrl(String channelArn) {
        try {
            return s3Client
                    .listObjectsV2(
                            ListObjectsV2Request.builder()
                                    .bucket(s3Properties.bucket())
                                    .prefix(RECORDING_PREFIX)
                                    .build())
                    .contents()
                    .stream()
                    .filter(object -> object.key().contains(channelIdOf(channelArn)))
                    .filter(object -> object.key().endsWith(LATEST_THUMBNAIL_SUFFIX))
                    .max(Comparator.comparing(S3Object::lastModified))
                    .map(object -> s3Properties.publicBaseUrl() + "/" + object.key());
        } catch (RuntimeException e) {
            log.warn("라이브 썸네일을 찾지 못했다 - channelArn={}", channelArn, e);
            return Optional.empty();
        }
    }

    // 녹화 경로에는 채널 ARN 대신 그 끝의 채널 아이디가 들어간다.
    private String channelIdOf(String channelArn) {
        return channelArn.substring(channelArn.lastIndexOf('/') + 1);
    }
}
