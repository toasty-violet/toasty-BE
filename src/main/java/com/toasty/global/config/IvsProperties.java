package com.toasty.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** IVS는 7개 리전에서만 제공된다. 미지원 리전을 넣으면 채널 생성이 실패한다. */
// recordingConfigurationArn은 방송 화면을 주기적으로 S3에 저장해 홈 카드 썸네일로 쓰는 설정이다.
// 비워 두면 녹화 없이 방송만 나가고 썸네일은 만들어지지 않는다.
@ConfigurationProperties(prefix = "aws.ivs")
public record IvsProperties(String region, String channelType, String recordingConfigurationArn) {

    public boolean recordsThumbnails() {
        return recordingConfigurationArn != null && !recordingConfigurationArn.isBlank();
    }
}
