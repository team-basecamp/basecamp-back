package com.basecamp.backend.domain.post.dto.response;

import com.basecamp.backend.domain.post.entity.PostReport;
import java.time.LocalDateTime;

// 게시글 신고 접수 응답 DTO. 엔티티를 직접 노출하지 않고 이 DTO로 변환해 반환한다.
public record PostReportResponse(
    // 접수된 신고 id (post_reports PK). 신고 그 자체를 가리킨다.
    Long reportId,
    // 신고 대상 게시글 id (posts PK). reportId와 다른 시퀀스라 값이 서로 다르다.
    // 프론트가 어떤 글을 신고했는지 맥락을 바로 쓸 수 있도록 함께 내려준다.
    Long postId,
    // 신고 접수 일시. 프론트의 "○분 전 신고됨" 표시·정렬에 쓸 수 있다.
    LocalDateTime createdAt,
    // 사용자에게 보여줄 접수 안내 메시지
    String message) {

  // 엔티티 → DTO 변환 정적 팩토리. 접수 성공 메시지를 함께 담는다.
  // report.getPost()는 서비스에서 findById로 이미 로딩한 게시글이라 추가 쿼리가 발생하지 않는다.
  public static PostReportResponse from(PostReport report) {
    return new PostReportResponse(
        report.getReportId(), report.getPost().getPostId(), report.getCreatedAt(), "신고가 접수되었습니다.");
  }
}
