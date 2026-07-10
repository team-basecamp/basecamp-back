package com.basecamp.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 설정.
 *
 * <p>소셜 로그인은 코드 릴레이 방식(REST)이라 {@code oauth2Login()}을 쓰지 않고, 무상태(STATELESS) JWT 인증만 구성한다.</p>
 * <p>refresh 토큰을 HttpOnly 쿠키로 주고받으므로 CORS는 credentials 허용 + 특정 오리진 화이트리스트로 설정한다.</p>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, CookieProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

	private static final String[] PUBLIC_ENDPOINTS = {
			"/api/v1/auth/login/**",
			"/api/v1/auth/token/refresh"
	};

	private static final String[] SWAGGER_ENDPOINTS = {
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/v3/api-docs/**"
	};

	private final JwtTokenProvider jwtTokenProvider;
	private final TokenBlacklistCache tokenBlacklistCache;
	private final UserRevocationCache userRevocationCache;
	private final CorsProperties corsProperties;
	private final ObjectMapper objectMapper;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_ENDPOINTS).permitAll()
						.requestMatchers(SWAGGER_ENDPOINTS).permitAll()
						// 캠핑장 목록/검색/상세 조회는 비로그인 상태에서도 볼 수 있어야 하므로 공개.
						.requestMatchers(HttpMethod.GET, "/api/v1/camps/**").permitAll()
						// TODO: 로그인(AuthController) 구현 전까지 임시로 공개. 로그인 붙으면 ADMIN 권한으로 되돌릴 것.
						.requestMatchers(HttpMethod.POST, "/api/v1/camps/fetch").permitAll()
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.exceptionHandling(handler -> handler
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
