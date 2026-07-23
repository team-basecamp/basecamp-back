package com.basecamp.backend.domain.review.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Builder;

// 리뷰 수정 요청 DTO. 컨트롤러에서 @Valid로 검증한 뒤 서비스로 전달한다.
@Builder
public record ReviewUpdateRequest(
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
        String content,

    // 수정 후에도 남길 "기존" 이미지의 상대경로 목록(예: /images/abc123.jpg).
    @Schema(description = "수정 후에도 남길 기존 이미지의 상대경로 목록. 생략하면 전부 유지, 빈 배열이면 전부 삭제")
        @Size(max = 50, message = "유지할 이미지는 최대 50개까지 지정할 수 있습니다.")
        List<
                @NotBlank(message = "유지할 이미지 경로는 비어 있을 수 없습니다.")
                // 접두어를 "/images/" 로 못 박지 않는다 — file.upload.url-prefix 로 바뀔 수 있고
                @Pattern(
                    regexp = "(?!.*\\.\\.)/[^\\s/]+(/[^\\s/]+)*",
                    message = "유지할 이미지 경로는 \"/\" 로 시작하는 상대경로여야 합니다.")
                String>
            keepImageUrls) {}
