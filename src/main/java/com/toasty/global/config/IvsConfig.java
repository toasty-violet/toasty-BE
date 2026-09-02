package com.toasty.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ivs.IvsClient;

@Configuration
public class IvsConfig {

    // credentialsProvider를 지정하지 않으면 SDK 기본 자격증명 체인을 쓴다.
    // 루트 .env는 Spring 프로퍼티로만 들어가고 OS 환경변수가 되지 않으므로,
    // 로컬 자격증명은 셸 환경변수나 aws profile로 준다.
    @Bean
    public IvsClient ivsClient(AwsProperties awsProperties) {
        return IvsClient.builder().region(Region.of(awsProperties.region())).build();
    }
}
