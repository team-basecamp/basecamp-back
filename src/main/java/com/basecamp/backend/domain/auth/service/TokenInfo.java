package com.basecamp.backend.domain.auth.service;

import java.time.Instant;

/**
 * 폐기 대상 토큰(access/refresh)에서 뽑아낸 최소 정보. 토큰 원문은 저장하지 않으므로 {@code jti} 만 나른다.
 *
 * @param expiresAt 원 토큰의 만료 시각. 블랙리스트 레코드의 수명(및 Redis 캐시 TTL)을 정하는 데 쓴다.
 */
public record TokenInfo(String jti, Instant expiresAt) {
}
