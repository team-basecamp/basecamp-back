package com.basecamp.backend.domain.auth.service;

import java.time.Instant;

/**
 * 폐기 대상 토큰에서 뽑아낸 최소 정보. 토큰 원문은 저장하지 않으므로 {@code jti} 만 나른다.
 *
 * @param expiresAt 원 토큰의 만료 시각. 블랙리스트 레코드의 수명(및 Redis 캐시 TTL)을 정하는 데 쓴다.
 * @param accessToken access 토큰이면 {@code true}. 인증 필터가 조회하는 Redis 캐시에는 access 토큰만 올린다.
 */
public record TokenInfo(String jti, Instant expiresAt, boolean accessToken) {

  public static TokenInfo access(String jti, Instant expiresAt) {
    return new TokenInfo(jti, expiresAt, true);
  }

  public static TokenInfo refresh(String jti, Instant expiresAt) {
    return new TokenInfo(jti, expiresAt, false);
  }
}
