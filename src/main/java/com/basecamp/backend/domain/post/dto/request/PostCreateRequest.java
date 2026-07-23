package com.basecamp.backend.domain.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

// 게시글 작성 요청 DTO. 컨트롤러에서 @Valid로 검증한 뒤 서비스로 전달한다.
@Builder
public record PostCreateRequest(
    // 게시판 카테고리 (GENERAL / CAMP_MATE / RESERVATION_TRANSFER 중 하나)
    @NotBlank(message = "카테고리는 필수입니다.")
        @Size(max = 30, message = "카테고리는 최대 30자까지 입력 가능합니다.")
        @Pattern(
            regexp = "GENERAL|CAMP_MATE|RESERVATION_TRANSFER",
            message = "카테고리는 GENERAL, CAMP_MATE, RESERVATION_TRANSFER 중 하나여야 합니다.")
        String category,

    // 게시글 제목 (최대 200자)
    @NotBlank(message = "제목은 필수입니다.") @Size(max = 200, message = "제목은 최대 200자까지 입력 가능합니다.")
        String title,

    // 게시글 본문
    @NotBlank(message = "내용은 필수입니다.") String content) {}
