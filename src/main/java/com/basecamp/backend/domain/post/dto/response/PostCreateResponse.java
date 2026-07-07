package com.basecamp.backend.domain.post.dto.response;

import com.basecamp.backend.domain.post.entity.Post;

import java.time.LocalDateTime;

// 게시글 조회(상세) 응답 DTO. 엔티티를 직접 노출하지 않고 이 DTO로 변환해 반환한다.
public record PostCreateResponse(
        Long postId,
        Long userId,
        String category,
        String title,
        String content,
        Integer viewCount,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    // 엔티티 → DTO 변환 정적 팩토리
    public static PostCreateResponse from(Post post) {
        return new PostCreateResponse(
                post.getPostId(),
                post.getUserId(),
                post.getCategory(),
                post.getTitle(),
                post.getContent(),
                post.getViewCount(),
                post.getStatus(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
