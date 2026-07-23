package com.basecamp.backend.domain.admin.dto.response;

import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 관리자용 게시글 상세. 회원용 {@code PostDetailResponse} 와 달리 상태와 무관하게(블라인드/삭제 포함) 원문을 그대로 담고, 관리자 판단에 필요한
 * {@code status}·{@code blindReason} 을 함께 노출한다.
 *
 * <p>{@code post.getUser()} LAZY 연관에 접근하므로 {@link #from} 은 작성자가 함께 로딩된 상태에서 호출돼야 한다({@code
 * PostRepository.findWithUserByPostId} 의 fetch join 참고).
 */
@Schema(description = "관리자용 게시글 상세")
public record AdminPostDetailResponse(
    @Schema(description = "게시글 ID", example = "15") Long postId,
    @Schema(description = "작성자 회원 ID", example = "7") Long userId,
    @Schema(description = "작성자 닉네임", example = "캠퍼") String nickname,
    @Schema(description = "게시판 카테고리", example = "RESERVATION_TRANSFER") String category,
    @Schema(description = "게시글 제목", example = "예약 양도합니다") String title,
    @Schema(description = "게시글 본문") String content,
    @Schema(description = "조회수", example = "42") Integer viewCount,
    @Schema(description = "게시글 상태 (ACTIVE / BLINDED / DELETED)", example = "BLINDED") String status,
    @Schema(description = "블라인드 처리 사유 (블라인드 상태가 아니면 null)", example = "부적절한 내용") String blindReason,
    @Schema(description = "작성 일시") LocalDateTime createdAt,
    @Schema(description = "수정 일시 (수정 전이면 null)") LocalDateTime updatedAt) {

  public static AdminPostDetailResponse from(Post post) {
    User user = post.getUser();
    return new AdminPostDetailResponse(
        post.getPostId(),
        user.getId(),
        user.getNickname(),
        post.getCategory().name(),
        post.getTitle(),
        post.getContent(),
        post.getViewCount(),
        post.getStatus().name(),
        post.getBlindReason(),
        post.getCreatedAt(),
        post.getUpdatedAt());
  }
}
