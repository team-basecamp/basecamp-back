package com.basecamp.backend.domain.post.repository;

import com.basecamp.backend.domain.post.entity.PostReport;
import com.basecamp.backend.domain.post.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostReportRepository extends JpaRepository<PostReport, Long> {

  // 같은 회원이 같은 게시글을 아직 처리되지 않은(PENDING) 상태로 이미 신고했는지 확인한다.
  // 중복 신고를 막기 위한 용도로, 처리(ACCEPTED/REJECTED)된 과거 신고는 재신고를 막지 않는다.
  boolean existsByPost_PostIdAndReporter_IdAndStatus(
      Long postId, Long reporterId, ReportStatus status);

  // 관리자 신고 목록 조회. 처리 상태로 걸러 페이지 단위로 반환한다.
  // 응답 DTO가 대상 게시글(제목·상태)과 신고자(닉네임)를 필요로 하는데 둘 다 LAZY 연관이라,
  // @EntityGraph로 함께 로딩해 목록 변환 시점의 N+1을 없앤다. (둘 다 ToOne이라 페이징이 온전하다)
  // count 쿼리는 Spring Data가 EntityGraph 없이 따로 생성한다.
  @EntityGraph(attributePaths = {"post", "reporter"})
  Page<PostReport> findByStatus(ReportStatus status, Pageable pageable);

  // 특정 게시글의 아직 처리되지 않은(PENDING) 신고들을 일괄 ACCEPTED로 바꾼다.
  // 관리자가 글을 블라인드하면 그 글에 쌓인 대기 신고는 조치가 끝난 것이므로 큐에서 내린다.
  // 벌크 UPDATE라 영속성 컨텍스트를 우회한다. 실행 전 flush(flushAutomatically)로 아직 반영되지 않은 변경(블라인드 처리 등)을
  // DB에 먼저 내보내고, 실행 후 clear(clearAutomatically)로 남아 있을 수 있는 stale 엔티티를 비운다.
  // 이 순서가 메서드 안에 갇혀 있어야 호출부가 flush 시점을 신경 쓰지 않아도 안전하다.
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
            update PostReport r
               set r.status = com.basecamp.backend.domain.post.entity.ReportStatus.ACCEPTED
             where r.post.postId = :postId
               and r.status = com.basecamp.backend.domain.post.entity.ReportStatus.PENDING
            """)
  int acceptPendingReportsByPost(@Param("postId") Long postId);
}
