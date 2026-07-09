package com.basecamp.backend.domain.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;



@Builder
public record PostCreateRequest(
        @NotBlank(message = "카테고리는 필수입니다.")
        @Size(max = 30, message = "카테고리는 최대 30자까지 입력 가능합니다.")
        // GENERAL / CAMP_MATE / RESERVATION_TRANSFER 셋 중 하나만 허용
        @Pattern(
                regexp = "GENERAL|CAMP_MATE|RESERVATION_TRANSFER",
                message = "카테고리는 GENERAL, CAMP_MATE, RESERVATION_TRANSFER 중 하나여야 합니다."
        )
        String category,

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 최대 200자까지 입력 가능합니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content) {
}
