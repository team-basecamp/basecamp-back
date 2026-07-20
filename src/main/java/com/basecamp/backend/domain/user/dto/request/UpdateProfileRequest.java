package com.basecamp.backend.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 내 프로필 수정 요청. 소셜 회원이 직접 바꿀 수 있는 값(닉네임 · 프로필 이미지)만 담는다.
 *
 * <p>이메일 · 소셜 제공자 · 권한 · 상태는 회원이 바꿀 수 없으므로 요청에 포함하지 않는다. {@code profileImageUrl} 을 비우면(null/빈 문자열)
 * 프로필 이미지를 제거한다.
 */
public record UpdateProfileRequest(
    @Schema(description = "닉네임", example = "캠퍼")
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 50, message = "닉네임은 50자를 넘을 수 없습니다.")
        String nickname,
    @Schema(
            description = "프로필 이미지 URL. 비우면 이미지를 제거한다",
            example = "https://cdn.example.com/profile/1.png")
        @Size(max = 2048, message = "프로필 이미지 URL 이 너무 깁니다.")
        String profileImageUrl) {}
