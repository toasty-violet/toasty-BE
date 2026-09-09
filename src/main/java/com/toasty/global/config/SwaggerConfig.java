package com.toasty.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    /** Swagger UI에 태그가 나오는 순서. 컨트롤러를 새로 만들면 그 태그 이름도 여기에 넣어야 한다. */
    // 여기 없는 태그는 목록 맨 뒤에 붙는다.
    private static final List<String> TAG_ORDER =
            List.of("Auth", "User", "Seller", "Live", "Seller Product");

    /** 태그 안에서 API가 나오는 순서. 유저가 겪는 순서대로 놓는다. */
    // 여기 없는 경로는 뒤에 경로 이름순으로 붙는다.
    private static final List<String> PATH_ORDER =
            List.of(
                    "/api/v1/login/kakao",
                    "/api/v1/logout",
                    "/api/v1/refresh",
                    "/api/v1/delete-user");

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

    /** 완성된 문서의 태그 목록을 TAG_ORDER대로 다시 세운다. */
    // 태그는 컨트롤러의 @Tag를 모아 만들어지는데 그 과정에서 순서가 정해지지 않아, 문서가 다 만들어진 뒤에 정렬한다.
    // OpenAPI 빈에 tags를 직접 넣는 방법도 있지만, 그러면 컨트롤러가 선언한 태그와 합쳐지지 않고 설명 없는 태그가 따로 생긴다.
    @Bean
    public OpenApiCustomizer tagOrderCustomizer() {
        return openApi -> {
            if (openApi.getTags() == null) {
                return;
            }
            List<Tag> sorted = new ArrayList<>(openApi.getTags());
            sorted.sort(Comparator.comparingInt(SwaggerConfig::orderOf));
            openApi.setTags(sorted);
        };
    }

    private static int orderOf(Tag tag) {
        int index = TAG_ORDER.indexOf(tag.getName());
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    /** 완성된 문서의 API 목록을 PATH_ORDER대로 다시 세운다. */
    // Swagger UI는 태그로 묶어 보여주므로, 전체를 한 줄로 세워두면 각 태그 안에서도 이 순서가 그대로 나온다.
    // PATH_ORDER에 없는 경로는 이름순으로 세워, 새 API가 늘어도 목록 순서가 매번 바뀌지 않게 한다.
    @Bean
    public OpenApiCustomizer pathOrderCustomizer() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths == null) {
                return;
            }
            Paths sorted = new Paths();
            sorted.setExtensions(paths.getExtensions());
            paths.keySet().stream()
                    .sorted(
                            Comparator.<String>comparingInt(SwaggerConfig::orderOf)
                                    .thenComparing(Comparator.naturalOrder()))
                    .forEach(path -> sorted.addPathItem(path, paths.get(path)));
            openApi.setPaths(sorted);
        };
    }

    private static int orderOf(String path) {
        int index = PATH_ORDER.indexOf(path);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    // Authorize 창에 토큰만 붙여넣으면 Bearer 접두어는 Swagger가 알아서 붙인다
    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
    }
}
