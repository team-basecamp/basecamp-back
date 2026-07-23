package com.basecamp.backend.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 캠핑업체 전환 신청 반려 요청. 사유는 {@code camp_owner_applications.reject_reason}(VARCHAR 200)에 저장된다. */
public record RejectApplicationRequest(
    @Schema(description = "반려 사유", example = "사업자등록증과 상호명이 일치하지 않습니다.")
        @NotBlank(message = "반려 사유는 필수입니다.")
        @Size(max = 200, message = "반려 사유는 200자를 넘을 수 없습니다.")
        String reason) {}
