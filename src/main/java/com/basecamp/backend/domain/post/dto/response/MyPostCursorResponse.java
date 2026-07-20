package com.basecamp.backend.domain.post.dto.response;

import com.basecamp.backend.domain.post.dto.request.PostCursorRequest;
import com.basecamp.backend.domain.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

// 마이페이지 "내가 쓴 게시글" 목록 응답 envelope (커서 페이징).
// 공용 목록(PostListCursorResponse)과 정렬 키 · 커서 형식이 같아 PostCursorRequest를 그대로 재사용한다.
// 무한 스크롤 프런트가 필요로 하는 건 "이번에 받은 목록", "더 있는지", "다음에 보낼 커서" 셋뿐이다.
public record MyPostCursorResponse(
    @Schema(description = "이번 페이지의 내 게시글 목록") List<MyPostResponse> content,
    @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext,
    @Schema(description = "다음 요청에 cursor로 그대로 실어 보낼 값. hasNext가 false면 null.") String nextCursor) {

  // limit+1건을 조회해 넘겨받는다. 한 건이 더 딸려 왔다면 다음 페이지가 있다는 뜻이다.
  // COUNT 쿼리 없이 hasNext를 알아내는 표준 수법이고, OFFSET과 달리 건너뛴 행을 읽는 비용도 없다.
  //
  // commentCounts: 이번 페이지 게시글들의 댓글 수를 post_id → count로 미리 집계한 값.
  // 댓글이 없는 게시글은 맵에 키가 없으므로 0으로 채운다.
  public static MyPostCursorResponse of(
      List<Post> lookahead, int limit, Map<Long, Integer> commentCounts) {
    boolean hasNext = lookahead.size() > limit;

    // 초과분(마지막 1건)은 존재 여부 판단에만 쓰고 응답에서는 잘라낸다.
    List<Post> posts = hasNext ? lookahead.subList(0, limit) : lookahead;

    List<MyPostResponse> content =
        posts.stream()
            .map(post -> MyPostResponse.from(post, commentCounts.getOrDefault(post.getPostId(), 0)))
            .toList();

    // 다음 커서는 "이번에 실제로 내려준 마지막 글"이어야 한다.
    // 잘라내기 전의 초과분으로 커서를 만들면 그 한 건이 다음 페이지에서 건너뛰어진다.
    String nextCursor = hasNext ? toCursor(posts.get(posts.size() - 1)) : null;

    return new MyPostCursorResponse(content, hasNext, nextCursor);
  }

  private static String toCursor(Post last) {
    return new PostCursorRequest(last.getCreatedAt(), last.getPostId()).encode();
  }
}
