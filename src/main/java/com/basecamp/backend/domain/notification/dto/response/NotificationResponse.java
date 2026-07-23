package com.basecamp.backend.domain.notification.dto.response;

import com.basecamp.backend.domain.notification.entity.Notification;
import com.basecamp.backend.domain.notification.entity.NotificationTargetType;
import com.basecamp.backend.domain.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 알림 단건 응답. 목록 조회와 SSE 실시간 push 에 동일하게 쓰인다. */
public record NotificationResponse(
    @Schema(description = "알림 ID", example = "1") Long id,
    @Schema(description = "알림 종류") NotificationType type,
    @Schema(description = "알림 본문", example = "'해운대 오토캠핑장' 예약이 확정되었습니다.") String message,
    @Schema(description = "대상 종류") NotificationTargetType targetType,
    @Schema(description = "대상 엔티티 ID(예약/신청 등)", example = "42") Long targetId,
    @Schema(description = "읽음 여부") boolean isRead,
    @Schema(description = "생성 일시") LocalDateTime createdAt) {

  public static NotificationResponse from(Notification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getType(),
        notification.getMessage(),
        notification.getTargetType(),
        notification.getTargetId(),
        notification.isRead(),
        notification.getCreatedAt());
  }
}
