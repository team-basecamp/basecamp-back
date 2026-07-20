package com.basecamp.backend.common.enums;

/**
 * 회원 권한. {@code users.role} 컬럼(VARCHAR)에 문자열로 저장된다. Spring Security 권한 문자열은 {@code "ROLE_" +
 * name()} 규칙을 따른다.
 */
public enum Role {
  CUSTOMER,
  CAMP_OWNER,
  ADMIN
}
