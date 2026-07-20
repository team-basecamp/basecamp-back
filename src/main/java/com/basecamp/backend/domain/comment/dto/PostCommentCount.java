package com.basecamp.backend.domain.comment.dto;

// 게시글 목록의 "댓글 수" 컬럼을 채우기 위한 집계 projection.
// post_id 하나당 댓글 수 한 건에 대응하며, CommentRepository의 GROUP BY COUNT 결과를 담는다.
// 응답 DTO가 아니라 리포지토리 → 서비스 사이에서만 오가는 내부 조회 타입이라 dto/response가 아닌 dto 바로 아래 둔다.
public record PostCommentCount(Long postId, Long count) {}
