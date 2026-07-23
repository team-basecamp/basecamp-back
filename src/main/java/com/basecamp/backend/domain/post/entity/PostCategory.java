package com.basecamp.backend.domain.post.entity;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import java.util.Locale;

/**
 * 게시판 카테고리. {@code posts.category} 컬럼(VARCHAR)에 문자열로 저장된다.
 *
 * <ul>
 *   <li>{@code GENERAL} : 자유게시판
 *   <li>{@code CAMP_MATE} : 캠핑 메이트 모집
 *   <li>{@code RESERVATION_TRANSFER} : 예약 양도
 * </ul>
 */
public enum PostCategory {
  GENERAL,
  CAMP_MATE,
  RESERVATION_TRANSFER;

  /**
   * 요청으로 들어온 문자열을 카테고리로 변환한다. 앞뒤 공백을 제거하고 대문자로 정규화한 뒤 매칭한다. 정의되지 않은 값이면 {@link
   * ErrorCode#INVALID_INPUT_VALUE}(400)로 막는다.
   *
   * <p>요청 DTO의 {@code @Pattern}이 1차로 걸러 주지만, 서비스로 넘어온 값을 enum으로 확정하는 최종 관문이자 목록 조회처럼 DTO 검증을 거치지 않는
   * 경로의 방어선 역할도 한다.
   */
  public static PostCategory from(String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
    try {
      return PostCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
  }
}
