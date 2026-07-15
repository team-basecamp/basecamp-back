package com.basecamp.backend.domain.post.repository;

import com.basecamp.backend.domain.post.entity.PostReport;
import com.basecamp.backend.domain.post.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostReportRepository extends JpaRepository<PostReport, Long> {

    // 같은 회원이 같은 게시글을 아직 처리되지 않은(PENDING) 상태로 이미 신고했는지 확인한다.
    // 중복 신고를 막기 위한 용도로, 처리(ACCEPTED/REJECTED)된 과거 신고는 재신고를 막지 않는다.
    boolean existsByPost_PostIdAndReporter_IdAndStatus(Long postId, Long reporterId, ReportStatus status);
}
