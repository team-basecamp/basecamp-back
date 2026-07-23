package com.basecamp.backend.domain.auth.client;

import com.basecamp.backend.domain.user.entity.Provider;

/**
 * provider별 소셜 로그인 어댑터.
 *
 * <p>코드 릴레이 방식: 인가 코드를 받아 {@code code → 토큰 교환(client_secret 포함) → user-info 조회} 를 수행하고, provider별
 * 응답을 {@link OAuthUserInfo} 로 정규화해 반환한다.
 *
 * <p>카카오/구글/네이버 구현체가 각각 이 인터페이스를 구현하며, {@link SocialClientResolver} 가 {@link #provider()} 로 선택한다.
 */
public interface SocialClient {

  /** 이 어댑터가 담당하는 소셜 제공자. */
  Provider provider();

  /**
   * 인가 코드로 토큰을 교환하고 사용자 정보를 조회해 정규화한다.
   *
   * @param authorizationCode 프론트가 소셜에서 받아 전달한 인가 코드
   * @param state CSRF 방지용 state 값(네이버 토큰 교환에 필요). 카카오/구글은 사용하지 않으므로 {@code null} 가능.
   * @return 정규화된 소셜 사용자 정보
   */
  OAuthUserInfo fetchUserInfo(String authorizationCode, String state);
}
