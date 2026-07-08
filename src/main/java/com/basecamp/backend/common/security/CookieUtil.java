package com.basecamp.backend.common.security;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * refresh 토큰 쿠키 생성/삭제 유틸.
 *
 * <p>항상 HttpOnly 로 발급하며, Secure/SameSite/Path/이름은 {@link CookieProperties}(환경변수)로 제어한다.
 * 만료시간은 refresh 토큰 TTL({@link JwtProperties#getRefreshTokenExpiration()})과 맞춘다.</p>
 */
@Component
@RequiredArgsConstructor
public class CookieUtil {

	private final CookieProperties cookieProperties;
	private final JwtProperties jwtProperties;

	/**
	 * refresh 토큰을 담은 HttpOnly 쿠키를 생성한다. 컨트롤러가 {@code Set-Cookie} 헤더로 응답에 추가한다.
	 */
	public ResponseCookie createRefreshTokenCookie(String refreshToken) {
		return baseBuilder(refreshToken)
				.maxAge(Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()))
				.build();
	}

	/**
	 * refresh 토큰 쿠키를 즉시 만료(삭제)시키는 쿠키를 생성한다(로그아웃/탈퇴 시 사용).
	 */
	public ResponseCookie deleteRefreshTokenCookie() {
		return baseBuilder("")
				.maxAge(0)
				.build();
	}

	private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
		return ResponseCookie.from(cookieProperties.getName(), value)
				.httpOnly(true)
				.secure(cookieProperties.isSecure())
				.sameSite(cookieProperties.getSameSite())
				.path(cookieProperties.getPath());
	}

	public String getRefreshCookieName() {
		return cookieProperties.getName();
	}

}
