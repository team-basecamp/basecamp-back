package com.basecamp.backend.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 게시글 블라인드 처리 요청. 사유는 {@code posts.blind_reason}(VARCHAR 200)에 저장된다. */
public record BlindPostRequest(
    @Schema(description = "블라인드 처리 사유", example = "음란성/폭력성 게시물")
        @NotBlank(message = "블라인드 사유는 필수입니다.")
        @Size(max = 200, message = "블라인드 사유는 200자를 넘을 수 없습니다.")
        String reason) {}
