package com.basecamp.backend.domain.user.entity;

/**
 * 회원 상태. {@code users.status} 컬럼(VARCHAR)에 문자열로 저장된다.
 *
 * <ul>
 *   <li>{@code ACTIVE} : 정상
 *   <li>{@code BLACKLISTED} : 관리자에 의해 제재됨
 *   <li>{@code WITHDRAWN} : 탈퇴
 * </ul>
 */
public enum UserStatus {
  ACTIVE,
  BLACKLISTED,
  WITHDRAWN
}
