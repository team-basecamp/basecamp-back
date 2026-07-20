package com.basecamp.backend.domain.post.entity;

/**
 * 게시글 상태. {@code posts.status} 컬럼(VARCHAR)에 문자열로 저장된다.
 *
 * <ul>
 *   <li>{@code ACTIVE} : 정상 노출
 *   <li>{@code BLINDED} : 관리자에 의해 블라인드 처리됨
 *   <li>{@code DELETED} : 작성자가 소프트 삭제함
 * </ul>
 */
public enum PostStatus {
  ACTIVE,
  BLINDED,
  DELETED
}
