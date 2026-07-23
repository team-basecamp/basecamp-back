package com.basecamp.backend.domain.admin.dto.response;

import com.basecamp.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 제재된 회원 목록 항목. 프로필 이미지는 노출하지 않으므로 LAZY 연관에 접근하지 않는다. */
@Schema(description = "제재된 회원")
public record BlacklistedUserResponse(
    @Schema(description = "회원 ID", example = "1") Long userId,
    @Schema(description = "이메일", example = "camper@example.com") String email,
    @Schema(description = "닉네임", example = "캠퍼") String nickname,
    @Schema(description = "가입한 소셜 제공자", example = "KAKAO") String provider,
    @Schema(description = "제재 사유", example = "부적절한 게시글 반복 작성") String blacklistReason,
    @Schema(description = "제재 일시") LocalDateTime blacklistedAt) {

  public static BlacklistedUserResponse from(User user) {
    return new BlacklistedUserResponse(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        user.getProvider().name(),
        user.getBlacklistReason(),
        user.getBlacklistedAt());
  }
}
