package com.basecamp.backend.domain.post.dto.response;

import com.basecamp.backend.domain.post.entity.Post;
import java.time.LocalDateTime;

// 마이페이지 "내가 쓴 게시글" 한 줄에 대응하는 응답 DTO.
// 공용 목록(PostListResponse)과 달리 작성자는 항상 본인이라 nickname을 담지 않고,
// 마이페이지 화면이 요구하는 제목 · 작성일 · 조회수 · 댓글 수만 최소로 내려준다.
public record MyPostResponse(
    Long postId,
    String title,
    // 생성 시각(작성일)이다. 수정 시각(updatedAt)이 아니다.
    LocalDateTime createdAt,
    Integer viewCount,
    Integer commentCount) {

  // 엔티티 → DTO 변환 정적 팩토리.
  // commentCount는 목록 쿼리와 별도로 post_id 집계로 미리 구한 값을 받는다.
  // (게시글마다 count를 따로 조회하면 N+1이 되므로 페이지 단위로 한 번에 집계한다 — CommentRepository.countByPostIds 참고)
  public static MyPostResponse from(Post post, int commentCount) {
    return new MyPostResponse(
        post.getPostId(), post.getTitle(), post.getCreatedAt(), post.getViewCount(), commentCount);
  }
}
