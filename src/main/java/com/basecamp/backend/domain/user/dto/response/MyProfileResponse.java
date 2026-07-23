package com.basecamp.backend.domain.user.dto.response;

import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 내 프로필 조회/수정 응답. 마이페이지 헤더가 쓰는 값(닉네임 · 이메일 · 프로필 이미지 · 권한 · 제공자)을 담는다.
 *
 * <p>{@code profileImage} 는 LAZY 연관이므로 트랜잭션 안에서 매핑해야 한다(서비스가 {@code @Transactional} 로 감싼다).
 */
@Schema(description = "내 프로필")
public record MyProfileResponse(
    @Schema(description = "회원 ID", example = "1") Long userId,
    @Schema(description = "이메일", example = "camper@example.com") String email,
    @Schema(description = "닉네임", example = "캠퍼") String nickname,
    @Schema(
            description = "프로필 이미지 URL. 없으면 null",
            example = "https://cdn.example.com/profile/1.png")
        String profileImageUrl,
    @Schema(description = "가입한 소셜 제공자", example = "KAKAO") String provider,
    @Schema(description = "가입 일시") LocalDateTime createdAt) {

  public static MyProfileResponse from(User user) {
    Image profileImage = user.getProfileImage();
    return new MyProfileResponse(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        profileImage == null ? null : profileImage.getImageUrl(),
        user.getProvider().name(),
        user.getCreatedAt());
  }
}
