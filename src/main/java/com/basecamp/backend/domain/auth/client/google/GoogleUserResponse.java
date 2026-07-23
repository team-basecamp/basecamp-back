package com.basecamp.backend.domain.auth.client.google;

import com.basecamp.backend.domain.auth.client.OAuthUserInfo;
import com.basecamp.backend.domain.user.entity.Provider;

/**
 * 구글 OpenID Connect {@code userinfo} 응답. 필요한 필드만 매핑한다(그 외는 무시).
 *
 * <p>이메일/이름/사진은 동의 범위에 따라 누락될 수 있어 nullable 로 다룬다. email 미제공 시 상위 로그인 로직이 {@link
 * com.basecamp.backend.common.exception.ErrorCode#EMAIL_CONSENT_REQUIRED} 로 거부한다.
 */
public record GoogleUserResponse(String sub, String email, String name, String picture) {

  public OAuthUserInfo toOAuthUserInfo() {
    return new OAuthUserInfo(Provider.GOOGLE, email, name, picture);
  }
}
