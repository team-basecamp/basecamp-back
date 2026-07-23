package com.basecamp.backend.security;

import com.basecamp.backend.security.cache.TokenBlacklistCache;
import com.basecamp.backend.security.cache.UserRevocationCache;
import com.basecamp.backend.security.config.CookieProperties;
import com.basecamp.backend.security.config.CorsProperties;
import com.basecamp.backend.security.config.JwtProperties;
import com.basecamp.backend.security.handler.JwtAccessDeniedHandler;
import com.basecamp.backend.security.handler.JwtAuthenticationEntryPoint;
import com.basecamp.backend.security.jwt.JwtAuthenticationFilter;
import com.basecamp.backend.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 설정.
 *
 * <p>소셜 로그인은 코드 릴레이 방식(REST)이라 {@code oauth2Login()}을 쓰지 않고, 무상태(STATELESS) JWT 인증만 구성한다.
 *
 * <p>refresh 토큰을 HttpOnly 쿠키로 주고받으므로 CORS는 credentials 허용 + 특정 오리진 화이트리스트로 설정한다.
 *
 * <p>인가는 두 층위를 함께 쓴다. 굵은 규칙(경로 접두사)은 아래 {@code authorizeHttpRequests} 에, 메서드 하나에만 걸리는 세밀한 규칙은
 * {@code @PreAuthorize} 에 둔다. 후자를 위해 {@link EnableMethodSecurity} 를 켠다 — 켜두지 않으면
 * {@code @PreAuthorize} 가 <b>예외 없이 조용히 무시되어</b> 막힐 거라 믿는 API 가 열린 채 배포된다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, CookieProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

  private static final String[] PUBLIC_ENDPOINTS = {
    "/api/v1/auth/login/**",
    "/api/v1/auth/token/refresh",
    // 포트원 결제 웹훅. 포트원 서버가 호출하므로 우리 access 토큰을 붙일 수 없다.
    // 인증 대신 PortOneWebhookVerifier 의 HMAC 서명 검증이 이 엔드포인트를 지킨다.
    "/api/v1/payments/webhook"
  };

  private static final String[] SWAGGER_ENDPOINTS = {
    "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
  };

  private final JwtTokenProvider jwtTokenProvider;
  private final TokenBlacklistCache tokenBlacklistCache;
  private final UserRevocationCache userRevocationCache;
  private final CorsProperties corsProperties;
  private final ObjectMapper objectMapper;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    .requestMatchers(SWAGGER_ENDPOINTS)
                    .permitAll()
                    // 이미지는 MinIO 가 직접 서빙하므로(공개 버킷) 애플리케이션에 공개 경로를 둘 필요가 없다.
                    // 홈페이지 시/도 날씨 위젯은 비로그인 방문자도 봐야 하므로 공개한다.
                    .requestMatchers(HttpMethod.GET, "/api/v1/weather/**")
                    .permitAll()
                    // "내 캠핑장" 조회는 로그인한 소유자 본인만 볼 수 있어야 하므로, 아래 공개 규칙보다 먼저 인증을 요구한다.
                    // 먼저 매칭된 규칙이 이긴다. 순서를 바꾸면 /my 가 조용히 공개된다(CampControllerSecurityTest 가 잡는다).
                    .requestMatchers(HttpMethod.GET, "/api/v1/camps/my")
                    .authenticated()
                    // 캠핑장 목록/검색/상세 조회는 비로그인 상태에서도 볼 수 있어야 하므로 공개.
                    .requestMatchers(HttpMethod.GET, "/api/v1/camps/**")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            handler ->
                handler
                    .authenticationEntryPoint(new JwtAuthenticationEntryPoint(objectMapper))
                    .accessDeniedHandler(new JwtAccessDeniedHandler(objectMapper)))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistCache, userRevocationCache),
            UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    // cors.allowed-origins (환경변수 CORS_ALLOWED_ORIGINS)로 주입되는 오리진 화이트리스트.
    config.setAllowedOrigins(corsProperties.getAllowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("Authorization"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
