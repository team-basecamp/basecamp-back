package com.basecamp.backend.domain.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 메세지 각?
// 게시글 수정 요청 DTO. 엔티티를 직접 받지 않고 이 DTO로 검증 후 전달한다.
public record PostUpdateRequest(
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
