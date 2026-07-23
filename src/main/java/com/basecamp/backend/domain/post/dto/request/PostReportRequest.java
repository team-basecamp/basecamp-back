package com.basecamp.backend.domain.post.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 게시글 신고 요청 DTO. 컨트롤러에서 @Valid로 검증한 뒤 서비스로 전달한다.
// 신고 대상 postId는 경로 변수(PathVariable)를 신뢰하므로 본문에 담겨 와도 사용하지 않는다.
public record PostReportRequest(
    // 신고 사유 (SPAM / INAPPROPRIATE / ILLEGAL / ETC 중 하나)
    @Schema(
            description = "신고 사유",
            example = "SPAM",
            allowableValues = {"SPAM", "INAPPROPRIATE", "ILLEGAL", "ETC"})
        @NotBlank(message = "신고 사유는 필수입니다.")
        @Pattern(
            regexp = "SPAM|INAPPROPRIATE|ILLEGAL|ETC",
            message = "신고 사유는 SPAM, INAPPROPRIATE, ILLEGAL, ETC 중 하나여야 합니다.")
        String reason,

    // 신고 상세 내용 (선택 입력)
    @Schema(description = "신고 상세 내용", example = "광고성 게시글입니다.")
        @Size(max = 1000, message = "상세 내용은 최대 1000자까지 입력 가능합니다.")
        String description) {}
