package com.basecamp.backend.security.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * cookie.refresh.* 설정값 바인딩 (refresh 토큰 쿠키 속성).
 *
 * <p>배포 환경(cross-site)에서는 {@code same-site=None}, {@code secure=true} 로 환경변수 주입한다. {@code
 * same-site=None} 이면 브라우저 규칙상 {@code secure=true} 가 필수다.
 */
@Getter
@ConfigurationProperties(prefix = "cookie.refresh")
public class CookieProperties {

  private final String name;
  private final String path;
  private final String sameSite;
  private final boolean secure;

  public CookieProperties(
      @DefaultValue("refreshToken") String name,
      @DefaultValue("/api/v1/auth") String path,
      @DefaultValue("Lax") String sameSite,
      @DefaultValue("false") boolean secure) {
    this.name = name;
    this.path = path;
    this.sameSite = sameSite;
    this.secure = secure;
  }
}
