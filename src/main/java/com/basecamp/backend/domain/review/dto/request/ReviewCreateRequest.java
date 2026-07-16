package com.basecamp.backend.domain.review.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

// 리뷰 작성 요청 DTO. 컨트롤러에서 @Valid로 검증한 뒤 서비스로 전달한다.
// 대상 예약 id는 URL 경로(PathVariable)로, 작성자는 예약 소유자로 검증하므로 본문에는 평점·내용만 담는다.
@Builder
public record ReviewCreateRequest(
        // 평점 (1.0 ~ 5.0)
        @NotNull(message = "평점은 필수입니다.")
        @DecimalMin(value = "1", message = "평점은 최소 1 이상이어야 합니다.")
        @DecimalMax(value = "5", message = "평점은 최대 5 이하여야 합니다.")
        Integer rating,

        // 리뷰 본문
        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 1000, message = "리뷰는 최대 1000자까지 입력 가능합니다.")
        String content) {
}
