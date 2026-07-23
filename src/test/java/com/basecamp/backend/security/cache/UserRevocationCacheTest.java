package com.basecamp.backend.security.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.basecamp.backend.security.config.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
class UserRevocationCacheTest {

  private static final long ACCESS_TTL_MS = 1_800_000L;
  private static final Long USER_ID = 7L;
  private static final String KEY = "revoke:user:7";
  // 고정된 "지금" 시각. revoke 는 이 시각(epoch 초)을 값으로 저장한다.
  private static final long REVOKED_AT_SEC = 1_700_000_000L;

  @Mock private StringRedisTemplate redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  private UserRevocationCache cache;

  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    Clock clock = Clock.fixed(Instant.ofEpochSecond(REVOKED_AT_SEC), ZoneOffset.UTC);
    cache =
        new UserRevocationCache(
            redisTemplate, new JwtProperties("s".repeat(32), ACCESS_TTL_MS, 1L), clock);
  }

  @Test
  @DisplayName("revoke_무효화시각을값으로_access토큰수명만큼_TTL을설정한다")
  void revoke_무효화시각을값으로_access토큰수명만큼_TTL을설정한다() {
    // given & when
    cache.revoke(USER_ID);

    // then: 값은 무효화 시각(epoch 초). 이 시각 이전 발급 토큰만 거부하기 위함이다.
    verify(valueOperations)
        .set(KEY, Long.toString(REVOKED_AT_SEC), Duration.ofMillis(ACCESS_TTL_MS));
  }

  @Test
  @DisplayName("revoke_Redis쓰기실패_예외를삼키지않는다")
  void revoke_Redis쓰기실패_예외를삼키지않는다() {
    // given: 조용히 넘기면 무효화했는데 토큰이 살아 있는 상태가 된다(트랜잭션이 롤백되어야 한다).
    willThrow(new RedisConnectionFailureException("down"))
        .given(valueOperations)
        .set(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(Duration.class));

    // when & then
    assertThatThrownBy(() -> cache.revoke(USER_ID))
        .isInstanceOf(RedisConnectionFailureException.class);
  }

  @Test
  @DisplayName("clear_무효화키를삭제한다")
  void clear_무효화키를삭제한다() {
    // given & when
    cache.clear(USER_ID);

    // then
    verify(redisTemplate).delete(KEY);
  }

  @Test
  @DisplayName("isRevoked_무효화시각이전에_발급된토큰_true를반환한다")
  void isRevoked_무효화시각이전에_발급된토큰_true를반환한다() {
    // given: revokedAt 보다 1초 앞서 발급된 옛 토큰.
    given(valueOperations.get(KEY)).willReturn(Long.toString(REVOKED_AT_SEC));
    Instant oldToken = Instant.ofEpochSecond(REVOKED_AT_SEC - 1);

    // when & then
    assertThat(cache.isRevoked(USER_ID, oldToken)).isTrue();
  }

  @Test
  @DisplayName("isRevoked_무효화이후_재로그인으로발급된토큰_false를반환한다")
  void isRevoked_무효화이후_재로그인으로발급된토큰_false를반환한다() {
    // given: #119 핵심. 승격 무효화 직후 재로그인해 받은 새 토큰(iat ≥ revokedAt)은 통과해야 한다.
    given(valueOperations.get(KEY)).willReturn(Long.toString(REVOKED_AT_SEC));
    Instant sameSecond = Instant.ofEpochSecond(REVOKED_AT_SEC); // 같은 초 → 통과
    Instant later = Instant.ofEpochSecond(REVOKED_AT_SEC + 5);

    // when & then
    assertThat(cache.isRevoked(USER_ID, sameSecond)).isFalse();
    assertThat(cache.isRevoked(USER_ID, later)).isFalse();
  }

  @Test
  @DisplayName("isRevoked_무효화표시없음_false를반환한다")
  void isRevoked_무효화표시없음_false를반환한다() {
    // given: 키 자체가 없다.
    given(valueOperations.get(KEY)).willReturn(null);

    // when & then
    assertThat(cache.isRevoked(USER_ID, Instant.ofEpochSecond(REVOKED_AT_SEC - 100))).isFalse();
  }

  @Test
  @DisplayName("isRevoked_구버전값_회원전체를무효화한다")
  void isRevoked_구버전값_회원전체를무효화한다() {
    // given: 이전 버전이 남긴 값 "1". 배포 전환기에 제재 키가 뚫리지 않도록 무효화로 취급한다.
    given(valueOperations.get(KEY)).willReturn("1");

    // when & then: 발급 시각과 무관하게 거부.
    assertThat(cache.isRevoked(USER_ID, Instant.ofEpochSecond(REVOKED_AT_SEC + 100))).isTrue();
  }

  @Test
  @DisplayName("isRevoked_Redis조회실패_failopen으로_false를반환한다")
  void isRevoked_Redis조회실패_failopen으로_false를반환한다() {
    // given
    given(valueOperations.get(KEY)).willThrow(new RedisConnectionFailureException("down"));

    // when & then: 재로그인·재발급은 여전히 MySQL 의 status 로 막히므로, 노출 창은 access 토큰 잔여 수명뿐이다.
    assertThat(cache.isRevoked(USER_ID, Instant.ofEpochSecond(REVOKED_AT_SEC - 100))).isFalse();
  }
}
