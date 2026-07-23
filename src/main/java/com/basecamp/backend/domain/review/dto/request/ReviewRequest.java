package com.basecamp.backend.domain.review.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;

// 리뷰 작성/수정 요청 DTO. 컨트롤러에서 @Valid로 검증한 뒤 서비스로 전달한다.
@Builder
public record ReviewRequest(
    // 평점 (1.0 ~ 5.0)
    @Schema(description = "평점")
        @NotNull(message = "평점은 필수입니다.")
        @DecimalMin(value = "1", message = "평점은 최소 1 이상이어야 합니다.")
        @DecimalMax(value = "5", message = "평점은 최대 5 이하여야 합니다.")
        Integer rating,

    // 리뷰 본문
    @Schema(description = "리뷰 내용")
        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 1000, message = "리뷰는 최대 1000자까지 입력 가능합니다.")
        String content) {}
