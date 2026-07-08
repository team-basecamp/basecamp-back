package com.basecamp.backend.domain.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;


// postId는 자동생성해주니 없어도 됨 다 끝인듯
@Builder
public record PostCreateRequest(
        @NotBlank
        @Size(max = 30)
        // GENERAL / CAMP_MATE / RESERVATION_TRANSFER 셋 중 하나만 허용
        @Pattern(regexp = "GENERAL|CAMP_MATE|RESERVATION_TRANSFER")
        String category,

        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        String content) {
}
