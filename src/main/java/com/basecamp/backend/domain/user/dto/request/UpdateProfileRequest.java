package com.basecamp.backend.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 내 프로필 수정 요청의 JSON 파트. 소셜 회원이 직접 바꿀 수 있는 값(닉네임 · 프로필 이미지)만 담는다.
 *
 * <p>프로필 이미지는 더 이상 URL 문자열로 받지 않고 파일 업로드로 받는다({@code image} 멀티파트 파트). 이미지를 어떻게 다룰지는 파일 유무와 {@code
 * removeImage} 조합으로 정한다.
 *
 * <ul>
 *   <li>{@code image} 파일이 있으면 → 그 파일로 교체한다({@code removeImage} 는 무시).
 *   <li>{@code image} 없이 {@code removeImage=true} → 기존 이미지를 제거한다.
 *   <li>{@code image} 없이 {@code removeImage=false}(기본) → 이미지는 그대로 둔다.
 * </ul>
 *
 * <p>이메일 · 소셜 제공자 · 권한 · 상태는 회원이 바꿀 수 없으므로 요청에 포함하지 않는다.
 */
public record UpdateProfileRequest(
    @Schema(description = "닉네임", example = "캠퍼")
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 50, message = "닉네임은 50자를 넘을 수 없습니다.")
        String nickname,
    @Schema(description = "이미지 파일 없이 기존 프로필 이미지를 제거하려면 true. 파일을 함께 보내면 무시된다.", example = "false")
        boolean removeImage) {}
