package com.basecamp.backend.domain.comment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

// 댓글 작성 요청 DTO. 컨트롤러에서 @Valid로 검증한 뒤 서비스로 전달한다.
// 대상 게시글 id는 URL 경로(PathVariable)로, 작성자 id는 토큰(AuthUser)으로 받으므로 본문에는 content만 담는다.
@Builder
public record CommentCreateRequest(
    // 댓글 본문
    @NotBlank(message = "내용은 필수입니다.") @Size(max = 1000, message = "댓글은 최대 1000자까지 입력 가능합니다.")
        String content) {}
