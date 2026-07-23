package com.basecamp.backend.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회원 제재(강제 로그아웃) 요청. 사유는 {@code users.blacklist_reason}(VARCHAR 200)에 저장된다. */
public record BlacklistUserRequest(
    @Schema(description = "제재 사유", example = "부적절한 게시글 반복 작성")
        @NotBlank(message = "제재 사유는 필수입니다.")
        @Size(max = 200, message = "제재 사유는 200자를 넘을 수 없습니다.")
        String reason) {}
