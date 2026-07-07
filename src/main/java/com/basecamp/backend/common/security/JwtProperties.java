package com.basecamp.backend.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;

/**
 * jwt.* 설정값 바인딩.
 * <ul>
 *     <li>{@code jwt.secret} : HMAC 서명 키</li>
 *     <li>{@code jwt.access-token-expiration} : Access Token 만료시간(ms)</li>
 *     <li>{@code jwt.refresh-token-expiration} : Refresh Token 만료시간(ms)</li>
 * </ul>
 */
@Getter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

	private final String secret;
	private final long accessTokenExpiration;
	private final long refreshTokenExpiration;

	public JwtProperties(String secret, long accessTokenExpiration, long refreshTokenExpiration) {
		this.secret = secret;
		this.accessTokenExpiration = accessTokenExpiration;
		this.refreshTokenExpiration = refreshTokenExpiration;
	}

}
