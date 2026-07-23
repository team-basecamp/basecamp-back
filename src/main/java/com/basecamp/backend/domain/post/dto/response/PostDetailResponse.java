package com.basecamp.backend.domain.post.dto.response;

import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;

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
    // 첨부 이미지 상대경로 목록(예: /images/abc123.jpg). 첨부 순서대로, 없으면 빈 목록.
    List<String> imageUrls,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  // 엔티티 → DTO 변환 정적 팩토리.
  // post.getUser()로 연관 User를 조인해 nickname을 채운다. (호출은 트랜잭션 안에서 이뤄져 LAZY 초기화가 안전하다.)
  // post.getImages()도 LAZY라 같은 트랜잭션 안에서 초기화해 상대경로만 뽑아 담는다.
  // (상세 조회 경로에서는 findWithUserByPostId가 이미 fetch join으로 채워둬 여기서 추가 쿼리가 나가지 않는다.)
  public static PostDetailResponse from(Post post) {
    return from(post, post.getViewCount());
  }

  // 조회수만 따로 받는 변환. 상세 조회에서 쓴다.
  // 조회수 증가는 JPQL 벌크 update(increaseViewCount)로 DB에서만 이뤄지고 메모리의 Post는 옛 값을 그대로 들고 있어,
  // post.getViewCount()를 쓰면 방금 올린 1이 빠진 값이 내려간다. 증가 후 값을 호출자가 넘겨 그 차이를 메운다.
  public static PostDetailResponse from(Post post, Integer viewCount) {
    User user = post.getUser();
    List<String> imageUrls = post.getImages().stream().map(Image::getImageUrl).toList();
    return new PostDetailResponse(
        post.getPostId(),
        user.getId(),
        user.getNickname(),
        post.getCategory().name(),
        post.getTitle(),
        post.getContent(),
        viewCount,
        post.getStatus().name(),
        imageUrls,
        post.getCreatedAt(),
        post.getUpdatedAt());
  }
}
