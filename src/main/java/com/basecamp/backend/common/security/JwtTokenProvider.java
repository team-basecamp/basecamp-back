package com.basecamp.backend.common.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;

/**
 * JWT(access/refresh) 생성 · 검증 · 파싱 담당.
 *
 * <p>클레임 구성: {@code sub}=userId, {@code role}=회원 권한, {@code type}=access|refresh</p>
 * <p>인증 필터는 이 Provider가 파싱한 클레임만으로 인증을 구성한다(무상태).</p>
 */
@Getter
@Component
public class JwtTokenProvider {

	public static final String CLAIM_ROLE = "role";
	public static final String CLAIM_TYPE = "type";
	public static final String TOKEN_TYPE_ACCESS = "access";
	public static final String TOKEN_TYPE_REFRESH = "refresh";

	private final SecretKey key;
	private final long accessTokenExpiration;
	private final long refreshTokenExpiration;

	public JwtTokenProvider(JwtProperties jwtProperties) {
		this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
		this.accessTokenExpiration = jwtProperties.getAccessTokenExpiration();
		this.refreshTokenExpiration = jwtProperties.getRefreshTokenExpiration();
	}

	public String createAccessToken(Long userId, String role) {
		return createToken(userId, role, TOKEN_TYPE_ACCESS, accessTokenExpiration);
	}

	public String createRefreshToken(Long userId, String role) {
		return createToken(userId, role, TOKEN_TYPE_REFRESH, refreshTokenExpiration);
	}

	private String createToken(Long userId, String role, String type, long expirationMs) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim(CLAIM_ROLE, role)
				.claim(CLAIM_TYPE, type)
				.issuedAt(now)
				.expiration(expiry)
				.signWith(key)
				.compact();
	}

	/**
	 * 서명 · 만료를 검증하고 클레임을 반환한다.
	 *
	 * @throws io.jsonwebtoken.ExpiredJwtException 만료된 토큰
	 * @throws io.jsonwebtoken.JwtException        서명 불일치 등 유효하지 않은 토큰
	 */
	public Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public Long getUserId(Claims claims) {
		return Long.valueOf(claims.getSubject());
	}

	public String getRole(Claims claims) {
		return claims.get(CLAIM_ROLE, String.class);
	}

	public String getType(Claims claims) {
		return claims.get(CLAIM_TYPE, String.class);
	}

}
