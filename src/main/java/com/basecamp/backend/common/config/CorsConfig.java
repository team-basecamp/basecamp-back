package com.basecamp.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS (Cross-Origin Resource Sharing) 설정
 *
 * 프론트엔드가 다른 포트(5173)에서 백엔드 API(8080)에 접근할 수 있도록 허용
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")  // /api/로 시작하는 모든 요청에 대해
                .allowedOrigins("http://localhost:3000","http://localhost:5173")  // localhost:5173에서의 요청 허용
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // 허용할 HTTP 메서드
                .allowedHeaders("*")  // 모든 헤더 허용
                .allowCredentials(true)  // 쿠키/인증정보 허용
                .maxAge(3600);  // 캐시 시간 (1시간)
    }
}