package com.basecamp.backend.domain.post.dto.response;

import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.user.entity.User;

import java.time.LocalDateTime;

// 게시글 작성 응답 DTO. 엔티티를 직접 노출하지 않고 이 DTO로 변환해 반환한다.
// 작성자 식별용 userId와 함께, 화면에 바로 보여줄 작성자 nickname을 포함한다.
public record PostDetailResponse(
        Long postId,
        Long userId,
        String nickname,
        String category,
        String title,
        String content,
        Integer viewCount,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    // 엔티티 → DTO 변환 정적 팩토리.
    // post.getUser()로 연관 User를 조인해 nickname을 채운다. (호출은 트랜잭션 안에서 이뤄져 LAZY 초기화가 안전하다.)
    public static PostDetailResponse from(Post post) {
        User user = post.getUser();
        return new PostDetailResponse(
                post.getPostId(),
                user.getId(),
                user.getNickname(),
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
