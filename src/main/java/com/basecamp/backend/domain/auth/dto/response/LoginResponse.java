package com.basecamp.backend.domain.auth.dto.response;

import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.User;

/** 로그인 응답 body. access token 은 여기에 담고, refresh token 은 별도로 HttpOnly 쿠키로 내려간다. */
public record LoginResponse(
    String accessToken,
    String tokenType,
    Long userId,
    String email,
    String nickname,
    String role,
    String profileImageUrl) {

  private static final String BEARER = "Bearer";

  /** LAZY 연관({@code profileImage}) 접근이 필요하므로 반드시 트랜잭션 내부에서 호출해야 한다. */
  public static LoginResponse from(User user, String accessToken) {
    Image image = user.getProfileImage();
    return new LoginResponse(
        accessToken,
        BEARER,
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        user.getRole().name(),
        image != null ? image.getImageUrl() : null);
  }
}
