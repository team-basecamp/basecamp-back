package com.basecamp.backend.domain.user.entity;

/**
 * 소셜 로그인 제공자. {@code users.provider} 컬럼(VARCHAR)에 문자열로 저장된다.
 */
public enum Provider {
	KAKAO,
	GOOGLE,
	NAVER
}
