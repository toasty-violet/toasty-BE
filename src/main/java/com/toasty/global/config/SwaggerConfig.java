package com.toasty.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Toasty API").description("Toasty 서버 API 문서").version("v1"))
                // Swagger UI의 Authorize에 넣은 토큰을 모든 요청에 실어 보낸다.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(
                        new Components()
                                .addSecuritySchemes(BEARER_SCHEME_NAME, bearerScheme())
                                .addResponses("400", new ApiResponse().description("잘못된 요청"))
                                .addResponses("401", new ApiResponse().description("인증 실패"))
                                .addResponses("403", new ApiResponse().description("접근 권한 없음"))
                                .addResponses("500", new ApiResponse().description("서버 오류")));
    }

    // Authorize 창에 토큰만 붙여넣으면 Bearer 접두어는 Swagger가 알아서 붙인다
    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
    }
}
