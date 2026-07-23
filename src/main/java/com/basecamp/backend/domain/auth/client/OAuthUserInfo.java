package com.basecamp.backend.domain.auth.client;

import com.basecamp.backend.domain.user.entity.Provider;

/**
 * provider(카카오/구글/네이버)별로 형태가 다른 소셜 사용자 정보를 공통 형태로 정규화한 모델.
 *
 * <p>{@code email} 은 회원 식별의 유일 키다(#23). provider 응답에서 이메일이 없으면 로그인 서비스가 가입을 거부한다.
 *
 * @param provider 소셜 제공자
 * @param email 소셜 계정 이메일 (식별 키)
 * @param nickname 표시 닉네임
 * @param profileImageUrl 프로필 이미지 URL (없으면 {@code null})
 */
public record OAuthUserInfo(
    Provider provider, String email, String nickname, String profileImageUrl) {}
