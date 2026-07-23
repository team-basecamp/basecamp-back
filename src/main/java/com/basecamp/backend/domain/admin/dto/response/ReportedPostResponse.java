package com.basecamp.backend.domain.admin.dto.response;

import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.entity.PostReport;
import com.basecamp.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 관리자 신고 목록의 한 항목(신고 1건). 대상 게시글과 신고자 정보를 함께 담아 관리자가 바로 판단할 수 있게 한다.
 *
 * <p>{@code post}/{@code reporter} LAZY 연관에 접근하므로 {@link #from} 은 두 연관이 함께 로딩된 상태에서 호출돼야 한다({@code
 * PostReportRepository.findByStatus} 의 {@code @EntityGraph} 참고).
 */
@Schema(description = "신고된 게시글 항목")
public record ReportedPostResponse(
    @Schema(description = "신고 ID", example = "10") Long reportId,
    @Schema(description = "신고 대상 게시글 ID", example = "42") Long postId,
    @Schema(description = "게시글 제목", example = "예약 양도합니다") String postTitle,
    @Schema(description = "게시글 현재 상태 (ACTIVE / BLINDED / DELETED)", example = "ACTIVE")
        String postStatus,
    @Schema(description = "게시판 카테고리", example = "RESERVATION_TRANSFER") String category,
    @Schema(description = "신고 사유 코드", example = "INAPPROPRIATE") String reason,
    @Schema(description = "신고 상세 내용", example = "부적절한 사진이 포함되어 있습니다.") String description,
    @Schema(description = "신고 처리 상태 (PENDING / ACCEPTED / REJECTED)", example = "PENDING")
        String reportStatus,
    @Schema(description = "신고자 회원 ID", example = "7") Long reporterId,
    @Schema(description = "신고자 닉네임", example = "캠퍼") String reporterNickname,
    @Schema(description = "신고 접수 일시") LocalDateTime createdAt) {

  public static ReportedPostResponse from(PostReport report) {
    Post post = report.getPost();
    User reporter = report.getReporter();
    return new ReportedPostResponse(
        report.getReportId(),
        post.getPostId(),
        post.getTitle(),
        post.getStatus().name(),
        post.getCategory().name(),
        report.getReason(),
        report.getDescription(),
        report.getStatus().name(),
        reporter.getId(),
        reporter.getNickname(),
        report.getCreatedAt());
  }
}
