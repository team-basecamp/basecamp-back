package com.basecamp.backend.domain.auth.client.kakao;

import com.basecamp.backend.domain.auth.client.OAuthUserInfo;
import com.basecamp.backend.domain.user.entity.Provider;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 {@code /v2/user/me} 응답. 필요한 필드만 매핑한다(그 외는 무시).
 *
 * <p>이메일/닉네임/프로필이미지는 사용자의 동의 항목에 따라 누락될 수 있어 모두 nullable 로 다룬다. 특히 email 은 미동의 시 {@code null} 이며, 이
 * 경우 상위 로그인 로직이 {@link com.basecamp.backend.common.exception.ErrorCode#EMAIL_CONSENT_REQUIRED} 로
 * 거부한다.
 */
public record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

  public OAuthUserInfo toOAuthUserInfo() {
    String email = kakaoAccount != null ? kakaoAccount.email() : null;
    KakaoProfile profile = kakaoAccount != null ? kakaoAccount.profile() : null;
    String nickname = profile != null ? profile.nickname() : null;
    String profileImageUrl = profile != null ? profile.profileImageUrl() : null;
    return new OAuthUserInfo(Provider.KAKAO, email, nickname, profileImageUrl);
  }

  public record KakaoAccount(String email, KakaoProfile profile) {}

  public record KakaoProfile(
      String nickname, @JsonProperty("profile_image_url") String profileImageUrl) {}
}
