package com.basecamp.backend.domain.admin.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.admin.dto.response.AdminPostDetailResponse;
import com.basecamp.backend.domain.admin.dto.response.ReportedPostResponse;
import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.entity.PostReport;
import com.basecamp.backend.domain.post.entity.ReportStatus;
import com.basecamp.backend.domain.post.repository.PostReportRepository;
import com.basecamp.backend.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 신고된 게시글 조회 및 블라인드 처리.
 *
 * <p>{@code /api/v1/admin/**} 는 {@code SecurityConfig} 에서 {@code ROLE_ADMIN} 만 허용하므로, 이 서비스는 권한 검사를
 * 다시 하지 않고 도메인 규칙만 담당한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AdminPostService {

  private final PostRepository postRepository;
  private final PostReportRepository postReportRepository;

  /**
   * 신고 목록 조회. 신고 1건이 한 행이며, 대상 게시글·신고자 정보를 함께 담아 반환한다.
   *
   * <p>{@code status} 를 생략하면 아직 처리되지 않은 {@code PENDING} 만 보여준다. 관리자가 실제로 조치할 대상이 대기 신고이기 때문이다. 처리
   * 이력({@code ACCEPTED}/{@code REJECTED})을 보려면 명시적으로 지정한다.
   */
  @Transactional(readOnly = true)
  public Page<ReportedPostResponse> findReports(ReportStatus status, Pageable pageable) {
    ReportStatus filter = (status == null) ? ReportStatus.PENDING : status;
    return postReportRepository.findByStatus(filter, pageable).map(ReportedPostResponse::from);
  }

  /**
   * 관리자용 게시글 단건 상세 조회.
   *
   * <p>회원용 조회({@code PostService#getDetail})와 달리 상태 게이팅을 하지 않는다. 관리자는 모더레이션·감사를 위해 블라인드({@code
   * BLINDED})·삭제({@code DELETED})된 글도 원문 그대로 열람할 수 있어야 한다. 조회 자체가 안 되는 경우, 즉 애초에 존재하지 않는 글만
   * 404({@code POST_NOT_FOUND})로 막는다.
   */
  @Transactional(readOnly = true)
  public AdminPostDetailResponse getPostDetail(Long postId) {
    // 응답에 작성자 nickname 이 필요하므로 fetch join 으로 작성자까지 함께 로딩한다. (상태 필터 없음)
    Post post =
        postRepository
            .findWithUserByPostId(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

    return AdminPostDetailResponse.from(post);
  }

  /**
   * 게시글을 블라인드 처리하고, 그 글에 쌓인 대기 신고를 함께 정리한다.
   *
   * <p>상태 전이 규칙({@link Post#blind}): 이미 블라인드면 409, 삭제된 글이면 404. 블라인드가 성공한 뒤 해당 글의 {@code PENDING}
   * 신고를 {@code ACCEPTED} 로 내려, 조치가 끝난 신고가 큐에 계속 남지 않게 한다.
   */
  public void blindPost(Long postId, String reason) {
    Post post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

    // 상태 전이 검증은 엔티티가 던진다(BLINDED → 409, DELETED → 404).
    post.blind(reason);

    // 블라인드가 확정된 뒤에만 신고를 정리한다. 위에서 예외가 나면 여기 도달하지 않는다.
    // 이 벌크 UPDATE는 실행 전 flush로 위의 BLINDED 변경을 먼저 DB에 반영한다(acceptPendingReportsByPost 참고).
    postReportRepository.acceptPendingReportsByPost(postId);
  }

  /**
   * 신고 반려. 블라인드 등 조치 없이 신고 1건을 기각한다(PENDING → REJECTED).
   *
   * <p>블라인드({@link #blindPost})가 게시글 단위로 그 글의 대기 신고를 한꺼번에 {@code ACCEPTED} 로 정리하는 것과 달리, 반려는 신고
   * 1건({@code reportId}) 단위다. 상태 전이 규칙({@link PostReport#reject}): 이미 처리된 신고면 409, 없는 신고면 404.
   */
  public void rejectReport(Long reportId) {
    PostReport report =
        postReportRepository
            .findById(reportId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

    // 상태 전이 검증은 엔티티가 던진다(PENDING 이 아니면 409). 변경 감지로 커밋 시점에 UPDATE 된다.
    report.reject();
  }
}
