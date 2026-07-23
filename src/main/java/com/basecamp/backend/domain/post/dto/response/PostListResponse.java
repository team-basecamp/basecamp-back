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
    Integer commentCount) {

  // 엔티티 → DTO 변환 정적 팩토리.
  // post.getUser()에 접근하므로 호출 전에 작성자가 함께 로딩돼 있어야 한다. (리포지토리의 @EntityGraph 참고)
  // commentCount는 목록 쿼리와 별도로 post_id 집계로 미리 구한 값을 받는다.
  // (게시글마다 count를 따로 조회하면 N+1이 되므로 페이지 단위로 한 번에 집계한다 — CommentRepository.countByPostIds 참고)
  public static PostListResponse from(Post post, int commentCount) {
    return new PostListResponse(
        post.getPostId(),
        post.getCategory().name(),
        post.getTitle(),
        post.getUser().getNickname(),
        post.getCreatedAt(),
        post.getViewCount(),
        commentCount);
  }
}
