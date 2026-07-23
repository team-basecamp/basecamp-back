package com.basecamp.backend.domain.campowner.entity;

/**
 * 캠핑업체 권한 승격 신청의 심사 상태. {@code camp_owner_applications.status} 컬럼(VARCHAR)에 문자열로 저장된다.
 *
 * <p>V12 의 파생 컬럼({@code user_id_pending}, {@code business_number_approved})이 이 값의 문자열을 그대로 비교하므로,
 * 상수 이름을 바꾸면 마이그레이션도 함께 바꿔야 한다.
 */
public enum ApplicationStatus {
  PENDING,
  APPROVED,
  REJECTED
}
