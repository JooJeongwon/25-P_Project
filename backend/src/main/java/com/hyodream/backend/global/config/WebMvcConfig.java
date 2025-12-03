package com.hyodream.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 모든 API 주소에 대해
                .allowedOrigins("http://localhost:3000") // 프론트엔드 주소 허용 (React 기본 포트)
                // 나중에 배포 주소가 생기면 여기에 추가: .allowedOrigins("http://localhost:3000",
                // "http://hyodream.com")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 허용할 HTTP 메서드
                .allowedHeaders("*") // 모든 헤더 허용
                .allowCredentials(true) // 인증 정보(쿠키, 토큰 등) 허용
                .maxAge(3600); // 설정 캐시 시간 (1시간)
    }
}