package com.basecamp.backend.security.support;

import com.basecamp.backend.security.config.CookieProperties;
import com.basecamp.backend.security.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * refresh 토큰 쿠키 생성/삭제 유틸.
 *
 * <p>항상 HttpOnly 로 발급하며, Secure/SameSite/Path/이름은 {@link CookieProperties}(환경변수)로 제어한다. 만료시간은
 * refresh 토큰 TTL({@link JwtProperties#getRefreshTokenExpiration()})과 맞춘다.
 */
@Component
@RequiredArgsConstructor
public class CookieUtil {

  private final CookieProperties cookieProperties;
  private final JwtProperties jwtProperties;

  /** refresh 토큰을 담은 HttpOnly 쿠키를 생성한다. 컨트롤러가 {@code Set-Cookie} 헤더로 응답에 추가한다. */
  public ResponseCookie createRefreshTokenCookie(String refreshToken) {
    return baseBuilder(refreshToken)
        .maxAge(Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()))
        .build();
  }

  /** refresh 토큰 쿠키를 즉시 만료(삭제)시키는 쿠키를 생성한다(로그아웃/탈퇴 시 사용). */
  public ResponseCookie deleteRefreshTokenCookie() {
    return baseBuilder("").maxAge(0).build();
  }

  /**
   * 요청 쿠키에서 refresh 토큰을 꺼낸다. 쿠키 이름이 설정값이라 {@code @CookieValue}(상수만 허용) 대신 직접 읽는다.
   *
   * @return 쿠키가 없거나 값이 비어 있으면 {@link Optional#empty()}
   */
  public Optional<String> resolveRefreshToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> cookieProperties.getName().equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(StringUtils::hasText)
        .findFirst();
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
