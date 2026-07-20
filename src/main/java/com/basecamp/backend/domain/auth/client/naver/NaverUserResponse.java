package com.basecamp.backend.domain.auth.client.naver;

import com.basecamp.backend.domain.auth.client.OAuthUserInfo;
import com.basecamp.backend.domain.user.entity.Provider;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 네이버 {@code /v1/nid/me} 응답. 실제 사용자 정보는 {@code response} 안에 들어 있다(필요한 필드만 매핑).
 *
 * <p>이메일/닉네임/프로필이미지는 사용자의 동의 항목에 따라 누락될 수 있어 모두 nullable 로 다룬다. email 미제공 시 상위 로그인 로직이 {@link
 * com.basecamp.backend.common.exception.ErrorCode#EMAIL_CONSENT_REQUIRED} 로 거부한다.
 */
public record NaverUserResponse(String resultcode, String message, Response response) {

  public OAuthUserInfo toOAuthUserInfo() {
    String email = response != null ? response.email() : null;
    String nickname = resolveNickname();
    String profileImageUrl = response != null ? response.profileImage() : null;
    return new OAuthUserInfo(Provider.NAVER, email, nickname, profileImageUrl);
  }

  // 닉네임 동의를 안 했을 수 있어, nickname 이 없으면 name 으로 대체한다.
  private String resolveNickname() {
    if (response == null) {
      return null;
    }
    return response.nickname() != null ? response.nickname() : response.name();
  }

  public record Response(
      String id,
      String email,
      String nickname,
      String name,
      @JsonProperty("profile_image") String profileImage) {}
}
