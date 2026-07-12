package com.basecamp.backend.domain.post.dto.request;

import jakarta.validation.constraints.NotNull;

// 게시글 삭제 요청 DTO. 삭제할 게시글의 postId만 담아 전달한다.
public record PostDeleteRequest(
        // 삭제할 게시글 id
        @NotNull(message = "postId는 필수입니다.")
        Long postId) {
}
