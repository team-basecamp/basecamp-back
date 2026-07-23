package com.basecamp.backend.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 안읽은 알림 개수 응답. 헤더 뱃지 표시에 쓰인다. */
public record UnreadCountResponse(@Schema(description = "안읽은 알림 개수", example = "3") long count) {

  public static UnreadCountResponse of(long count) {
    return new UnreadCountResponse(count);
  }
}
