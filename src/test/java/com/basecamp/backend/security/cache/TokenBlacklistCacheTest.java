package com.basecamp.backend.security.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenBlacklistCacheTest {

  private static final String JTI = "0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9";
  private static final String KEY = "blacklist:" + JTI;
  private static final Instant NOW = Instant.parse("2026-07-09T00:00:00Z");

  @Mock private StringRedisTemplate redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  private TokenBlacklistCache cache;

  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    cache = new TokenBlacklistCache(redisTemplate);
  }

  @Test
  @DisplayName("blacklist_토큰의_남은수명만큼_TTL을설정한다")
  void blacklist_토큰의_남은수명만큼_TTL을설정한다() {
    // given & when
    cache.blacklist(JTI, NOW.plus(Duration.ofMinutes(30)), NOW);

    // then: 만료된 토큰은 서명 검증에서 걸러지므로 그 이후까지 캐시에 남길 이유가 없다.
    verify(valueOperations).set(eq(KEY), anyString(), eq(Duration.ofMinutes(30)));
  }

  @Test
  @DisplayName("blacklist_이미만료된토큰_TTL이_최소값으로보정된다")
  void blacklist_이미만료된토큰_TTL이_최소값으로보정된다() {
    // given: 만료 시각이 과거면 TTL 이 음수가 되어 Redis 가 거부한다.
    cache.blacklist(JTI, NOW.minusSeconds(10), NOW);

    // then
    verify(valueOperations).set(eq(KEY), anyString(), eq(Duration.ofSeconds(1)));
  }

  @Test
  @DisplayName("blacklist_Redis쓰기실패_예외를삼키지않는다")
  void blacklist_Redis쓰기실패_예외를삼키지않는다() {
    // given: 조용히 넘기면 로그아웃했는데 토큰이 살아 있는 상태가 된다.
    willThrow(new RedisConnectionFailureException("down"))
        .given(valueOperations)
        .set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));

    // when & then
    assertThatThrownBy(() -> cache.blacklist(JTI, NOW.plusSeconds(60), NOW))
        .isInstanceOf(RedisConnectionFailureException.class);
  }

  @Test
  @DisplayName("isBlacklisted_등록된jti_true를반환한다")
  void isBlacklisted_등록된jti_true를반환한다() {
    // given
    given(redisTemplate.hasKey(KEY)).willReturn(true);

    // when & then
    assertThat(cache.isBlacklisted(JTI)).isTrue();
  }

  @Test
  @DisplayName("isBlacklisted_Redis조회실패_faillopen으로_false를반환한다")
  void isBlacklisted_Redis조회실패_failopen으로_false를반환한다() {
    // given: Redis 가 죽어도 전체 인증을 막지 않는다(단일 장애점 방지).
    given(redisTemplate.hasKey(KEY)).willThrow(new RedisConnectionFailureException("down"));

    // when & then: Step 8 이전 동작(access 토큰은 만료 전까지 유효)으로 퇴화할 뿐이다.
    assertThat(cache.isBlacklisted(JTI)).isFalse();
  }
}
