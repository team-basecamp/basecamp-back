package com.basecamp.backend.domain.comment.dto.response;

import com.basecamp.backend.domain.comment.entity.Comment;
import com.basecamp.backend.domain.user.entity.User;

import java.time.LocalDateTime;

// 댓글 작성 응답 DTO. 엔티티를 직접 노출하지 않고 이 DTO로 변환해 반환한다.
// 어느 게시글의 댓글인지(postId), 작성자 식별용 userId와 함께, 화면에 바로 보여줄 작성자 nickname을 포함한다.
public record CommentResponse(
        Long commentId,
        Long postId,
        Long userId,
        String nickname,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    // 엔티티 → DTO 변환 정적 팩토리.
    // comment.getUser()로 연관 User를 조인해 nickname을 채운다. (호출은 트랜잭션 안에서 이뤄져 LAZY 초기화가 안전하다.)
    public static CommentResponse from(Comment comment) {
        User user = comment.getUser();
        return new CommentResponse(
                comment.getCommentId(),
                comment.getPost().getPostId(),
                user.getId(),
                user.getNickname(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
