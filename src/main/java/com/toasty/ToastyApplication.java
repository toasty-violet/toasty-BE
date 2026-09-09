package com.toasty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// @ConfigurationPropertiesScan: 설정용 record(CorsProperties 등)를 별도 등록 없이 빈으로 잡는다.
// @EnableScheduling: 종료된 라이브의 채팅방을 회수하는 배치가 돈다.
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ToastyApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToastyApplication.class, args);
    }
}
