package com.basecamp.backend.security.cache;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 폐기된 토큰({@code jti})의 조회 캐시.
 *
 * <p>영속 기록은 MySQL {@code token_blacklist} 가 담당하고, 여기(Redis)는 <b>검증 경로에서 조회하는 캐시</b>다(#39 §5).
 * access 토큰 검증은 매 API 요청마다 일어나므로 DB 를 직접 때리면 무상태 JWT 의 이점이 사라진다.
 *
 * <p>키는 토큰의 남은 수명만큼만 살아 있다가 TTL 로 자동 삭제된다. 만료된 토큰은 서명 검증 단계에서 이미 걸러지므로 캐시에 남겨둘 이유가 없다.
 *
 * <p><b>장애 정책:</b> 조회 실패는 <i>fail-open</i>(경고 로그 후 통과)이다. Redis 가 죽었다고 전체 인증을 막으면 단일 장애점이 된다. 이때
 * 동작은 Step 8 이전, 즉 "access 토큰은 만료 전까지 유효"로 퇴화할 뿐이다. 반면 <b>쓰기 실패는 삼키지 않는다</b>. 조용히 넘기면 로그아웃했는데 토큰이
 * 살아 있는 상태가 되기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistCache {

  private static final String KEY_PREFIX = "blacklist:";
  private static final String VALUE = "1";

  /** TTL 이 0 이하가 되지 않도록 하는 하한. 이미 만료된 토큰을 넣는 경우를 대비한다. */
  private static final Duration MIN_TTL = Duration.ofSeconds(1);

  private final StringRedisTemplate redisTemplate;

  /**
   * 토큰을 폐기 상태로 기록한다. 키는 원 토큰의 만료 시각까지만 유지된다.
   *
   * <p>실패 시 예외가 그대로 전파된다. 호출자(로그아웃/탈퇴/회전)는 폐기가 실패했음을 알아야 한다.
   */
  public void blacklist(String jti, Instant expiresAt, Instant now) {
    Duration ttl = Duration.between(now, expiresAt);
    if (ttl.compareTo(MIN_TTL) < 0) {
      ttl = MIN_TTL;
    }
    redisTemplate.opsForValue().set(KEY_PREFIX + jti, VALUE, ttl);
  }

  /**
   * 폐기된 토큰인지 확인한다.
   *
   * @return Redis 조회에 실패하면 {@code false}(fail-open)
   */
  public boolean isBlacklisted(String jti) {
    try {
      return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    } catch (RuntimeException e) {
      // fail-open: 폐기된 토큰이 만료 전까지 통과할 수 있음을 감수하고 서비스 가용성을 지킨다.
      log.warn("토큰 블랙리스트 캐시 조회 실패. 이 요청은 통과시킨다. jti={}", jti, e);
      return false;
    }
  }
}
