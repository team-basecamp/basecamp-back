package com.basecamp.backend.security.cache;

import com.basecamp.backend.security.config.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 회원 단위 access 토큰 무효화 표시.
 *
 * <p>제재(#18)·업체 승격(#53)의 본질은 "이 토큰을 죽여라"가 아니라 "이 사람의 (지금까지 발급된) 토큰을 죽여라"다. 무상태 JWT 라 서버가 발급된 토큰을
 * 보관하지 않으므로 대상 회원의 {@code jti} 를 알 수 없고, 따라서 토큰 단위 denylist({@link TokenBlacklistCache}) 로는 표현할 수
 * 없다. 대신 회원 식별자로 표시해 두고 인증 필터가 매 요청 확인한다.
 *
 * <p><b>값은 "무효화 시각(revokedAt, epoch 초)"이다.</b> 존재 여부만 보던 이전 방식은 무효화된 회원의 <i>모든</i> 토큰을 막아, 무효화 직후
 * 재로그인해 받은 <b>새 토큰까지</b> 거부했다(#119). 이제 {@code isRevoked} 는 토큰의 발급 시각(iat)과 revokedAt 을 비교해
 * <b>revokedAt 이전에 발급된 토큰만</b> 거부한다.
 *
 * <ul>
 *   <li>제재: 재로그인이 {@code users.status = BLACKLISTED} 로 막혀 새 토큰이 안 나오므로 옛 토큰만 죽는다(종전과 동일).
 *   <li>승격: DB status 는 정상이라 재로그인이 되고, 그때 받은 새 토큰(iat ≥ revokedAt)은 통과한다.
 * </ul>
 *
 * <p><b>TTL 은 access 토큰 수명이면 충분하다.</b> 그 시간이 지나면 revokedAt 이전 발급 토큰은 전부 만료된다.
 *
 * <p><b>장애 정책:</b> 조회는 <i>fail-open</i>, 쓰기는 예외를 전파한다({@link TokenBlacklistCache} 와 동일). Redis 가
 * 죽어도 재로그인·재발급은 MySQL 의 {@code status} 로 막히므로, 노출 창은 이미 발급된 access 토큰의 남은 수명으로 한정된다.
 */
@Slf4j
@Component
public class UserRevocationCache {

  private static final String KEY_PREFIX = "revoke:user:";

  // 저장된 값이 이 값보다 작으면 유효한 revokedAt 이 아니라고 본다. 구버전(존재만 표시하던 값 "1")이나 손상된
  // 값이 여기 걸린다. 그때는 안전한 쪽(회원 전체 무효화)으로 처리해, 배포 전환기에 제재 키가 뚫리지 않게 한다.
  // 2020-09-13(1_600_000_000) 이후 발급되는 토큰의 iat 는 항상 이 값보다 크다.
  private static final long MIN_PLAUSIBLE_EPOCH_SECOND = 1_600_000_000L;

  private final StringRedisTemplate redisTemplate;
  private final Duration ttl;
  private final Clock clock;

  public UserRevocationCache(
      StringRedisTemplate redisTemplate, JwtProperties jwtProperties, Clock clock) {
    this.redisTemplate = redisTemplate;
    this.ttl = Duration.ofMillis(jwtProperties.getAccessTokenExpiration());
    this.clock = clock;
  }

  /**
   * 지금 이 시각 이전에 발급된 회원의 access 토큰을 무효화한다. 이후 재로그인해 받은 토큰은 영향받지 않는다. 실패 시 예외가 전파되어 제재/승격 트랜잭션이 롤백된다.
   */
  public void revoke(Long userId) {
    long revokedAtSec = Instant.now(clock).getEpochSecond();
    redisTemplate.opsForValue().set(key(userId), Long.toString(revokedAtSec), ttl);
  }

  /** 무효화 표시를 제거한다. 실패 시 예외가 전파되어 해제 트랜잭션이 롤백된다(제재 상태가 유지된다). */
  public void clear(Long userId) {
    redisTemplate.delete(key(userId));
  }

  /**
   * 해당 토큰이 무효화 대상인지 확인한다. revokedAt 이전에 발급된(iat &lt; revokedAt) 토큰만 대상이다.
   *
   * <p>iat 와 revokedAt 은 모두 초 단위(JWT 표준)이고 비교는 {@code <} 다. 같은 초에 발급된 토큰은 통과시켜, 무효화 직후 재로그인으로 발급된
   * 토큰이 초 절삭 때문에 막히는 것을 피한다(옛 토큰이 최대 1초 더 사는 것은 무해).
   *
   * @param tokenIssuedAt 검증 중인 토큰의 발급 시각(iat)
   * @return Redis 조회에 실패하면 {@code false}(fail-open)
   */
  public boolean isRevoked(Long userId, Instant tokenIssuedAt) {
    try {
      String value = redisTemplate.opsForValue().get(key(userId));
      if (value == null) {
        return false; // 무효화된 적 없음
      }
      long revokedAtSec;
      try {
        revokedAtSec = Long.parseLong(value.trim());
      } catch (NumberFormatException e) {
        // 구버전/손상된 값 → 안전하게 회원 전체를 무효화(종전 동작 유지).
        return true;
      }
      if (revokedAtSec < MIN_PLAUSIBLE_EPOCH_SECOND) {
        return true;
      }
      return tokenIssuedAt.getEpochSecond() < revokedAtSec;
    } catch (RuntimeException e) {
      // fail-open: Redis 를 단일 장애점으로 만들지 않는다. 재로그인·재발급은 여전히 DB 로 막힌다.
      log.warn("회원 무효화 캐시 조회 실패. 이 요청은 통과시킨다. userId={}", userId, e);
      return false;
    }
  }

  private String key(Long userId) {
    return KEY_PREFIX + userId;
  }
}
