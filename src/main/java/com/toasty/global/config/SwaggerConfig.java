package com.toasty.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Toasty API").description("Toasty 서버 API 문서").version("v1"))
                .components(
                        new Components()
                                .addResponses("400", new ApiResponse().description("잘못된 요청"))
                                .addResponses("401", new ApiResponse().description("인증 실패"))
                                .addResponses("500", new ApiResponse().description("서버 오류")));
    }
}
