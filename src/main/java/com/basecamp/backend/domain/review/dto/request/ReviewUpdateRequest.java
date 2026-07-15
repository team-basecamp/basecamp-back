package com.basecamp.backend.domain.review.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

// 리뷰 수정 요청 DTO. 컨트롤러에서 @Valid로 검증한 뒤 서비스로 전달한다.
// 평점·본문 전체를 갈아끼우는 형태라 작성 요청과 동일한 검증 규칙을 둔다.
@Builder
public record ReviewUpdateRequest(
        // 평점 (1.0 ~ 5.0)
        @NotNull(message = "평점은 필수입니다.")
        @DecimalMin(value = "1.0", message = "평점은 최소 1.0 이상이어야 합니다.")
        @DecimalMax(value = "5.0", message = "평점은 최대 5.0 이하여야 합니다.")
        BigDecimal rating,

        // 리뷰 본문
        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 1000, message = "리뷰는 최대 1000자까지 입력 가능합니다.")
        String content) {
}
