package com.basecamp.backend.domain.admin.dto.response;

import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 관리자 회원 목록 항목(#19).
 *
 * <p>{@code profileImage} 는 LAZY 연관이므로 {@code UserRepository.findAll(Specification, Pageable)} 이
 * {@code @EntityGraph} 로 함께 로딩한 뒤에 매핑해야 한다. 다른 경로에서 이 DTO 를 만들면 N+1 이 된다.
 *
 * <p>제재 정보({@code blacklistReason}, {@code blacklistedAt})와 탈퇴 시각({@code deletedAt})은 해당 상태일 때만 값이
 * 있다. 탈퇴 사유는 회원이 직접 쓴 텍스트라 목록에 싣지 않는다.
 */
@Schema(description = "관리자 회원 목록 항목")
public record AdminUserResponse(
    @Schema(description = "회원 ID", example = "1") Long userId,
    @Schema(description = "이메일", example = "camper@example.com") String email,
    @Schema(description = "닉네임", example = "캠퍼") String nickname,
    @Schema(
            description = "프로필 이미지 URL. 없으면 null",
            example = "https://cdn.example.com/profile/1.png")
        String profileImageUrl,
    @Schema(description = "가입한 소셜 제공자", example = "KAKAO") String provider,
    @Schema(description = "권한", example = "CUSTOMER") String role,
    @Schema(description = "상태", example = "ACTIVE") String status,
    @Schema(description = "가입 일시") LocalDateTime createdAt,
    @Schema(description = "제재 사유. 제재 중일 때만 값이 있다", example = "부적절한 게시글 반복 작성")
        String blacklistReason,
    @Schema(description = "제재 일시. 제재 중일 때만 값이 있다") LocalDateTime blacklistedAt,
    @Schema(description = "탈퇴 일시. 탈퇴 회원만 값이 있다") LocalDateTime deletedAt) {

  public static AdminUserResponse from(User user) {
    Image profileImage = user.getProfileImage();
    return new AdminUserResponse(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        profileImage == null ? null : profileImage.getImageUrl(),
        user.getProvider().name(),
        user.getRole().name(),
        user.getStatus().name(),
        user.getCreatedAt(),
        user.getBlacklistReason(),
        user.getBlacklistedAt(),
        user.getDeletedAt());
  }
}
