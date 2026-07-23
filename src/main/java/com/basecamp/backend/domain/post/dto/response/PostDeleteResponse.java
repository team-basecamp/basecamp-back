package com.basecamp.backend.domain.post.dto.response;

// 게시글 삭제 응답 DTO. 서버가 HTTP 리다이렉트를 하지 않고,
// 프론트(React)가 클라이언트 라우팅으로 이동할 목적지 경로만 내려준다.
public record PostDeleteResponse(
    // 삭제 후 React가 이동할 목록 경로
    String redirectUrl) {

  // 게시글 목록으로 이동하는 응답
  public static PostDeleteResponse toPostList() {
    return new PostDeleteResponse("/api/v1/posts");
  }
}
