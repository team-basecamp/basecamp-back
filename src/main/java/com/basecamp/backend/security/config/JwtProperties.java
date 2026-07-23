package com.basecamp.backend.security.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * jwt.* 설정값 바인딩.
 *
 * <ul>
 *   <li>{@code jwt.secret} : HMAC 서명 키 (HMAC-SHA 최소 키 길이 충족을 위해 32자 이상)
 *   <li>{@code jwt.access-token-expiration} : Access Token 만료시간(ms, 양수)
 *   <li>{@code jwt.refresh-token-expiration} : Refresh Token 만료시간(ms, 양수)
 * </ul>
 *
 * <p>잘못된 값(빈/짧은 secret, 0 이하 TTL)이 {@link JwtTokenProvider}로 전달되기 전에 바인딩 시점에서 fail-fast 로 차단하기 위해
 * Bean Validation 을 적용한다.
 */
@Getter
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

  @NotBlank
  @Size(min = 32, message = "jwt.secret 은 HMAC-SHA 서명을 위해 최소 32자(256bit) 이상이어야 합니다.")
  private final String secret;

  @Positive(message = "jwt.access-token-expiration 은 0보다 커야 합니다.")
  private final long accessTokenExpiration;

  @Positive(message = "jwt.refresh-token-expiration 은 0보다 커야 합니다.")
  private final long refreshTokenExpiration;

  public JwtProperties(String secret, long accessTokenExpiration, long refreshTokenExpiration) {
    this.secret = secret;
    this.accessTokenExpiration = accessTokenExpiration;
    this.refreshTokenExpiration = refreshTokenExpiration;
  }
}
