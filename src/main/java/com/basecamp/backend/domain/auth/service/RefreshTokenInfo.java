package com.basecamp.backend.domain.auth.service;

import java.time.Instant;

/**
 * 폐기 대상 refresh 토큰에서 뽑아낸 최소 정보. 토큰 원문은 저장하지 않으므로 {@code jti} 만 나른다.
 *
 * @param expiresAt 원 토큰의 만료 시각. 이 시각이 지나면 블랙리스트 레코드를 정리해도 된다.
 */
public record RefreshTokenInfo(String jti, Instant expiresAt) {
}
