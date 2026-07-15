package com.basecamp.backend.domain.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.admin.dto.response.ReportedPostResponse;
import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.entity.ReportStatus;
import com.basecamp.backend.domain.post.repository.PostReportRepository;
import com.basecamp.backend.domain.post.repository.PostRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자의 신고된 게시글 조회 및 블라인드 처리.
 *
 * <p>{@code /api/v1/admin/**} 는 {@code SecurityConfig} 에서 {@code ROLE_ADMIN} 만 허용하므로,
 * 이 서비스는 권한 검사를 다시 하지 않고 도메인 규칙만 담당한다.</p>
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
	 * <p>{@code status} 를 생략하면 아직 처리되지 않은 {@code PENDING} 만 보여준다. 관리자가 실제로 조치할
	 * 대상이 대기 신고이기 때문이다. 처리 이력({@code ACCEPTED}/{@code REJECTED})을 보려면 명시적으로 지정한다.</p>
	 */
	@Transactional(readOnly = true)
	public Page<ReportedPostResponse> findReports(ReportStatus status, Pageable pageable) {
		ReportStatus filter = (status == null) ? ReportStatus.PENDING : status;
		return postReportRepository.findByStatus(filter, pageable).map(ReportedPostResponse::from);
	}

	/**
	 * 게시글을 블라인드 처리하고, 그 글에 쌓인 대기 신고를 함께 정리한다.
	 *
	 * <p>상태 전이 규칙({@link Post#blind}): 이미 블라인드면 409, 삭제된 글이면 404. 블라인드가 성공한 뒤
	 * 해당 글의 {@code PENDING} 신고를 {@code ACCEPTED} 로 내려, 조치가 끝난 신고가 큐에 계속 남지 않게 한다.</p>
	 */
	public void blindPost(Long postId, String reason) {
		Post post = postRepository.findById(postId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

		// 상태 전이 검증은 엔티티가 던진다(BLINDED → 409, DELETED → 404). (아직 영속성 컨텍스트에만, DB 미반영)
		post.blind(reason);

		// BLINDED 변경을 먼저 DB에 반영
		postRepository.flush();

		// 블라인드가 확정된 뒤에만 신고를 정리한다. 위에서 예외가 나면 여기 도달하지 않는다. 벌크 UPDATE + clearAutomatically=true
		postReportRepository.acceptPendingReportsByPost(postId);
	}
}
