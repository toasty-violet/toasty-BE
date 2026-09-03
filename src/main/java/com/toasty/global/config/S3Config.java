package com.toasty.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    /** 상품 사진 업로드 주소에 서명할 때 쓴다. */
    // 자격증명은 IvsConfig와 같은 이유로 SDK 기본 체인에서 가져온다.
    @Bean
    public S3Presigner s3Presigner(S3Properties s3Properties) {
        return S3Presigner.builder().region(Region.of(s3Properties.region())).build();
    }

    /** 업로드된 사진이 실제로 올라왔는지 확인할 때 쓴다. */
    @Bean
    public S3Client s3Client(S3Properties s3Properties) {
        return S3Client.builder().region(Region.of(s3Properties.region())).build();
    }
}
