package com.basecamp.backend.common.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;

/**
 * cors.* 설정값 바인딩.
 *
 * <p>{@code cors.allowed-origins} : credentials 허용 CORS의 오리진 화이트리스트.
 * 콤마 구분 문자열(환경변수) 또는 YAML 리스트로 지정 가능하며, 배포 오리진은 환경변수로 주입한다.</p>
 */
@Getter
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

	private final List<String> allowedOrigins;

	public CorsProperties(List<String> allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

}
