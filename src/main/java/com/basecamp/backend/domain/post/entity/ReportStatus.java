package com.basecamp.backend.domain.post.entity;

/**
 * 게시글 신고 처리 상태. {@code post_reports.status} 컬럼(VARCHAR)에 문자열로 저장된다.
 *
 * <ul>
 *   <li>{@code PENDING} : 접수됨(관리자 처리 대기)
 *   <li>{@code ACCEPTED} : 관리자가 승인 (블라인드 등 조치 완료)
 *   <li>{@code REJECTED} : 관리자가 반려
 * </ul>
 */
public enum ReportStatus {
  PENDING,
  ACCEPTED,
  REJECTED
}
