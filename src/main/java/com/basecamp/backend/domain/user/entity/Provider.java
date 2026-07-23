package com.basecamp.backend.domain.user.entity;

/**
 * 소셜 로그인 제공자. {@code users.provider} 컬럼(VARCHAR)에 문자열로 저장된다.
 *
 * <p>{@code displayName} 은 사용자에게 보여줄 한글 이름이다(예: 다른 소셜로 가입된 계정 안내 문구).
 */
public enum Provider {
  KAKAO("카카오"),
  GOOGLE("구글"),
  NAVER("네이버");

  private final String displayName;

  Provider(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
