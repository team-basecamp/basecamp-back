package com.basecamp.backend.security.jwt;

import com.basecamp.backend.security.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * JWT(access/refresh) 생성 · 검증 · 파싱 담당.
 *
 * <p>클레임 구성: {@code jti}=토큰 고유 식별자(UUID), {@code sub}=userId, {@code role}=회원 권한, {@code
 * type}=access|refresh
 *
 * <p>인증 필터는 이 Provider가 파싱한 클레임만으로 인증을 구성한다(무상태).
 *
 * <p>{@code jti} 는 무효화된 토큰을 블랙리스트에 기록·조회하기 위한 키다. 토큰 원문 대신 jti 를 저장하므로 저장소가 유출되어도 유효한 토큰이 함께 새지
 * 않는다.
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
        .id(UUID.randomUUID().toString())
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
   * @throws io.jsonwebtoken.JwtException 서명 불일치 등 유효하지 않은 토큰
   */
  public Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
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

  /** 블랙리스트 등록·조회 키로 쓰이는 토큰 고유 식별자. */
  public String getJti(Claims claims) {
    return claims.getId();
  }

  /**
   * 토큰 발급 시각(iat). {@link com.basecamp.backend.security.cache.UserRevocationCache} 가 "무효화 시각 이전에
   * 발급된 토큰"만 거부하도록 판단하는 데 쓴다.
   */
  public Instant getIssuedAt(Claims claims) {
    return claims.getIssuedAt().toInstant();
  }

  /**
   * 토큰 만료 시각. 블랙리스트 레코드의 수명(만료된 토큰은 서명 검증에서 이미 걸러지므로 더 보관할 필요가 없다)을 정하는 데 쓴다.
   *
   * <p>타임존 해석 없이 다루도록 {@link Instant} 로 반환한다. 저장 시점에 {@code Clock} 의 존으로 변환한다.
   */
  public Instant getExpiresAt(Claims claims) {
    return claims.getExpiration().toInstant();
  }
}
