package com.basecamp.backend.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 제재(강제 로그아웃)된 회원의 무효화 표시.
 *
 * <p>제재의 본질은 "이 토큰을 죽여라"가 아니라 "이 사람의 모든 토큰을 죽여라"다. 무상태 JWT 라 서버가 발급된 토큰을
 * 보관하지 않으므로 대상 회원의 {@code jti} 를 알 수 없고, 따라서 토큰 단위 denylist({@link TokenBlacklistCache})
 * 로는 제재를 표현할 수 없다. 대신 회원 식별자로 표시해 두고 인증 필터가 매 요청 확인한다(#18).</p>
 *
 * <p><b>TTL 은 access 토큰 수명이면 충분하다.</b> 그 시간이 지나면 살아 있는 access 토큰은 전부 제재 이후 발급분인데,
 * 제재 중에는 소셜 로그인과 토큰 재발급이 모두 {@code users.status = BLACKLISTED} 로 막혀 있어 애초에 발급되지 않는다.</p>
 *
 * <p><b>장애 정책:</b> 조회는 <i>fail-open</i>, 쓰기는 예외를 전파한다({@link TokenBlacklistCache} 와 동일).
 * Redis 가 죽어도 재로그인·재발급은 MySQL 의 {@code status} 로 막히므로, 노출 창은 이미 발급된 access 토큰의
 * 남은 수명으로 한정된다.</p>
 */
@Slf4j
@Component
public class UserRevocationCache {

	private static final String KEY_PREFIX = "revoke:user:";
	private static final String VALUE = "1";

	private final StringRedisTemplate redisTemplate;
	private final Duration ttl;

	public UserRevocationCache(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
		this.redisTemplate = redisTemplate;
		this.ttl = Duration.ofMillis(jwtProperties.getAccessTokenExpiration());
	}

	/** 회원의 모든 access 토큰을 무효화한다. 실패 시 예외가 전파되어 제재 트랜잭션이 롤백된다. */
	public void revoke(Long userId) {
		redisTemplate.opsForValue().set(key(userId), VALUE, ttl);
	}

	/** 제재 해제. 실패 시 예외가 전파되어 해제 트랜잭션이 롤백된다(제재 상태가 유지된다). */
	public void clear(Long userId) {
		redisTemplate.delete(key(userId));
	}

	/**
	 * 제재된 회원인지 확인한다.
	 *
	 * @return Redis 조회에 실패하면 {@code false}(fail-open)
	 */
	public boolean isRevoked(Long userId) {
		try {
			return Boolean.TRUE.equals(redisTemplate.hasKey(key(userId)));
		} catch (RuntimeException e) {
			// fail-open: Redis 를 단일 장애점으로 만들지 않는다. 재로그인·재발급은 여전히 DB 로 막힌다.
			log.warn("회원 제재 캐시 조회 실패. 이 요청은 통과시킨다. userId={}", userId, e);
			return false;
		}
	}

	private String key(Long userId) {
		return KEY_PREFIX + userId;
	}

}
