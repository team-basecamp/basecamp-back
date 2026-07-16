package com.basecamp.backend.security.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.time.Duration;

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

import com.basecamp.backend.security.config.JwtProperties;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserRevocationCacheTest {

	private static final long ACCESS_TTL_MS = 1_800_000L;
	private static final Long USER_ID = 7L;
	private static final String KEY = "revoke:user:7";

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private UserRevocationCache cache;

	@BeforeEach
	void setUp() {
		given(redisTemplate.opsForValue()).willReturn(valueOperations);
		cache = new UserRevocationCache(redisTemplate, new JwtProperties("s".repeat(32), ACCESS_TTL_MS, 1L));
	}

	@Test
	@DisplayName("revoke_access토큰수명만큼_TTL을설정한다")
	void revoke_access토큰수명만큼_TTL을설정한다() {
		// given & when
		cache.revoke(USER_ID);

		// then: 그 시간이 지나면 살아 있는 access 토큰은 전부 제재 이후 발급분인데, 제재 중엔 발급 자체가 막혀 있다.
		verify(valueOperations).set(KEY, "1", Duration.ofMillis(ACCESS_TTL_MS));
	}

	@Test
	@DisplayName("revoke_Redis쓰기실패_예외를삼키지않는다")
	void revoke_Redis쓰기실패_예외를삼키지않는다() {
		// given: 조용히 넘기면 제재했는데 토큰이 살아 있는 상태가 된다(트랜잭션이 롤백되어야 한다).
		willThrow(new RedisConnectionFailureException("down"))
				.given(valueOperations).set(org.mockito.ArgumentMatchers.anyString(),
						org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Duration.class));

		// when & then
		assertThatThrownBy(() -> cache.revoke(USER_ID)).isInstanceOf(RedisConnectionFailureException.class);
	}

	@Test
	@DisplayName("clear_제재키를삭제한다")
	void clear_제재키를삭제한다() {
		// given & when
		cache.clear(USER_ID);

		// then
		verify(redisTemplate).delete(KEY);
	}

	@Test
	@DisplayName("isRevoked_제재된회원_true를반환한다")
	void isRevoked_제재된회원_true를반환한다() {
		// given
		given(redisTemplate.hasKey(KEY)).willReturn(true);

		// when & then
		assertThat(cache.isRevoked(USER_ID)).isTrue();
	}

	@Test
	@DisplayName("isRevoked_Redis조회실패_failopen으로_false를반환한다")
	void isRevoked_Redis조회실패_failopen으로_false를반환한다() {
		// given
		given(redisTemplate.hasKey(KEY)).willThrow(new RedisConnectionFailureException("down"));

		// when & then: 재로그인·재발급은 여전히 MySQL 의 status 로 막히므로, 노출 창은 access 토큰 잔여 수명뿐이다.
		assertThat(cache.isRevoked(USER_ID)).isFalse();
	}

}
