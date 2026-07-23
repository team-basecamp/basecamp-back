package com.basecamp.backend.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.basecamp.backend.security.config.JwtProperties;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private static final String SECRET = "test-secret-key-for-jwt-token-provider-unit-test";
  private static final long ACCESS_TTL_MS = 1_800_000L;
  private static final long REFRESH_TTL_MS = 1_209_600_000L;
  private static final Long USER_ID = 42L;
  private static final String ROLE = "CUSTOMER";

  private final JwtTokenProvider provider =
      new JwtTokenProvider(new JwtProperties(SECRET, ACCESS_TTL_MS, REFRESH_TTL_MS));

  @Test
  @DisplayName("createAccessToken_발급시_jti가_UUID로_채워진다")
  void createAccessToken_발급시_jti가_UUID로_채워진다() {
    // given & when
    Claims claims = provider.parseClaims(provider.createAccessToken(USER_ID, ROLE));

    // then
    String jti = provider.getJti(claims);
    assertThat(jti).isNotBlank();
    assertThat(UUID.fromString(jti)).hasToString(jti); // UUID 형식이어야 CHAR(36) 컬럼에 들어간다
  }

  @Test
  @DisplayName("createToken_같은사용자로_두번발급시_jti가_서로다르다")
  void createToken_같은사용자로_두번발급시_jti가_서로다르다() {
    // given & when
    String first =
        provider.getJti(provider.parseClaims(provider.createRefreshToken(USER_ID, ROLE)));
    String second =
        provider.getJti(provider.parseClaims(provider.createRefreshToken(USER_ID, ROLE)));

    // then
    // jti 가 겹치면 회전 시 이전 토큰을 블랙리스트에 올리는 순간 새 토큰까지 함께 죽는다.
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  @DisplayName("getExpiresAt_refresh토큰_만료시각이_설정된_TTL과_일치한다")
  void getExpiresAt_refresh토큰_만료시각이_설정된_TTL과_일치한다() {
    // given
    Instant before = Instant.now();

    // when
    Claims claims = provider.parseClaims(provider.createRefreshToken(USER_ID, ROLE));

    // then
    // JWT exp 는 초 단위로 절삭되므로 1초 오차를 허용한다.
    Instant expected = before.plusMillis(REFRESH_TTL_MS);
    assertThat(provider.getExpiresAt(claims))
        .isBetween(expected.minusSeconds(1), expected.plusSeconds(2));
  }

  @Test
  @DisplayName("createToken_access와_refresh의_type클레임이_구분된다")
  void createToken_access와_refresh의_type클레임이_구분된다() {
    // given & when
    Claims access = provider.parseClaims(provider.createAccessToken(USER_ID, ROLE));
    Claims refresh = provider.parseClaims(provider.createRefreshToken(USER_ID, ROLE));

    // then
    // 재발급 엔드포인트가 access 토큰을 refresh 로 오인하지 않으려면 이 구분이 유지되어야 한다.
    assertThat(provider.getType(access)).isEqualTo(JwtTokenProvider.TOKEN_TYPE_ACCESS);
    assertThat(provider.getType(refresh)).isEqualTo(JwtTokenProvider.TOKEN_TYPE_REFRESH);
    assertThat(provider.getUserId(access)).isEqualTo(USER_ID);
    assertThat(provider.getRole(refresh)).isEqualTo(ROLE);
  }
}
