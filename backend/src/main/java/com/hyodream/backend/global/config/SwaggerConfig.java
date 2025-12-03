package com.hyodream.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        // 1. JWT 설정 (스웨거 페이지에 '자물쇠' 버튼 생김)
        String jwtSchemeName = "JWT Token";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP) // HTTP 방식
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        // 2. API 문서 정보 설정
        return new OpenAPI()
                .info(new Info()
                        .title("효드림(HyoDream) API 명세서")
                        .description("실버 세대를 위한 쇼핑몰 효드림의 백엔드 API 문서입니다.")
                        .version("1.0.0"))
                .addSecurityItem(securityRequirement)
                .components(components);
    }
}