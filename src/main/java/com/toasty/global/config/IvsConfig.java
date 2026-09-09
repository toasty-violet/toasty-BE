package com.toasty.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ivs.IvsClient;
import software.amazon.awssdk.services.ivschat.IvschatClient;

@Configuration
public class IvsConfig {

    // credentialsProvider를 지정하지 않으면 SDK 기본 자격증명 체인을 쓴다.
    // 루트 .env는 Spring 프로퍼티로만 들어가고 OS 환경변수가 되지 않으므로,
    // 로컬 자격증명은 셸 환경변수나 aws profile로 준다.
    @Bean
    public IvsClient ivsClient(IvsProperties ivsProperties) {
        return IvsClient.builder().region(Region.of(ivsProperties.region())).build();
    }

    // 채팅은 IVS와 별개 서비스라 클라이언트를 따로 만든다. 리전을 다르게 둘 이유가 없어 같은 값을 쓴다.
    @Bean
    public IvschatClient ivschatClient(IvsProperties ivsProperties) {
        return IvschatClient.builder().region(Region.of(ivsProperties.region())).build();
    }
}
