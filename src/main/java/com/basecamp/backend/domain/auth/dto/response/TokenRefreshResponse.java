package com.basecamp.backend.domain.auth.dto.response;

/** 토큰 재발급 응답 body. 새 access token 만 담고, 회전된 refresh token 은 HttpOnly 쿠키로 교체된다. */
public record TokenRefreshResponse(String accessToken, String tokenType) {

  private static final String BEARER = "Bearer";

  public static TokenRefreshResponse of(String accessToken) {
    return new TokenRefreshResponse(accessToken, BEARER);
  }
}
