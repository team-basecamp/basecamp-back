package com.basecamp.backend.domain.post.dto.response;

import com.basecamp.backend.domain.post.entity.Post;

import java.time.LocalDateTime;

// 게시글 목록 한 줄에 대응하는 응답 DTO. 상세(PostDetailResponse)와 달리 본문(content)은 담지 않는다.
public record PostListResponse(
        Long postId,
        String category,
        String title,
        String nickname,
        LocalDateTime createdAt,
        Integer viewCount,
        Integer commentCount
) {

    // TODO: 댓글 수 하드코딩. comment 도메인이 아직 목록 조회에 붙지 않아 임시로 10을 내려준다.
    //       comments 테이블이 붙으면 post_id로 GROUP BY COUNT한 값을 조회 쿼리에서 함께 가져와야 한다.
    //       게시글마다 count 쿼리를 따로 날리면 N+1이 되므로, 목록 쿼리에 집계를 합치거나
    //       posts에 comment_count 반정규화 컬럼을 두는 방식 중 하나를 골라야 한다.
    private static final Integer COMMENT_COUNT_PLACEHOLDER = 10;

    // 엔티티 → DTO 변환 정적 팩토리.
    // post.getUser()에 접근하므로 호출 전에 작성자가 함께 로딩돼 있어야 한다. (리포지토리의 @EntityGraph 참고)
    public static PostListResponse from(Post post) {
        return new PostListResponse(
                post.getPostId(),
                post.getCategory(),
                post.getTitle(),
                post.getUser().getNickname(),
                post.getCreatedAt(),
                post.getViewCount(),
                COMMENT_COUNT_PLACEHOLDER
        );
    }
}
