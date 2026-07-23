package com.basecamp.backend.domain.auth.repository;

import com.basecamp.backend.domain.auth.entity.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {

  /**
   * 해당 jti 의 토큰이 무효화되었는지 확인한다. {@code idx_bl_jti}(UNIQUE) 를 탄다.
   *
   * <p>Step 6 에서는 refresh 재발급 경로에서만 호출한다. access 토큰 검증(매 API 요청)에까지 적용하려면 조회 빈도가 급증하므로 Redis 캐시를 앞에
   * 두어야 한다(#39 §5).
   */
  boolean existsByJti(String jti);
}
