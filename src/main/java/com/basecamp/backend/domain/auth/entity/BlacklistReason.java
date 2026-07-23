package com.basecamp.backend.domain.auth.entity;

/** 토큰을 블랙리스트에 올린 사유. {@code token_blacklist.reason} 에 문자열로 저장된다. */
public enum BlacklistReason {

  /** refresh 토큰 회전(rotation)으로 폐기된 이전 토큰. 재제출되면 탈취 의심으로 간주한다. */
  REFRESH_ROTATED,

  /** 로그아웃으로 무효화된 토큰. */
  LOGOUT,

  /** 회원 탈퇴로 무효화된 토큰. */
  WITHDRAWAL
}
