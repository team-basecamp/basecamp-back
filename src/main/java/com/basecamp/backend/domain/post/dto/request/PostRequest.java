package com.basecamp.backend.domain.post.dto.request;

import lombok.Builder;


// postId는 자동생성해주니 없어도 됨 다 끝인듯
@Builder
public record PostRequest(
        String category,
        String title,
        String content) {
}
