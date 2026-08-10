package com.toasty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// @ConfigurationPropertiesScan: 설정용 record(CorsProperties 등)를 별도 등록 없이 빈으로 잡는다.
@SpringBootApplication
@ConfigurationPropertiesScan
public class ToastyApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToastyApplication.class, args);
    }
}
