package com.basecamp.backend.domain.notification.entity;

import lombok.Getter;

/**
 * 알림 종류. {@code notifications.type} 컬럼(VARCHAR)에 문자열로 저장된다(#92).
 *
 * <p>각 상수는 (1) 사용자에게 보여줄 메시지 템플릿과 (2) 알림이 가리키는 대상 종류({@link NotificationTargetType})를 가진다. {@code
 * %s} 자리에는 캠핑장 이름 등 동적 값이 들어가며, 자리표시자가 없는 템플릿(업체 전환 결과)은 인자를 무시한다. 문구와 대상 종류를 한곳에 모아 두어 발생
 * 지점(예약/관리자 도메인)이 이를 알 필요가 없게 한다.
 */
@Getter
public enum NotificationType {
  RESERVATION_APPROVE_WAIT(NotificationTargetType.RESERVATION, "'%s' 예약 신청이 완료되어 승인을 기다리고 있습니다."),
  RESERVATION_CONFIRMED(NotificationTargetType.RESERVATION, "'%s' 예약이 확정되었습니다."),
  RESERVATION_REJECTED(NotificationTargetType.RESERVATION, "'%s' 예약이 거절되었습니다."),
  RESERVATION_REQUESTED(NotificationTargetType.RESERVATION, "'%s' 새 예약 신청이 접수되어 승인 대기 중입니다."),
  RESERVATION_CANCELLED(NotificationTargetType.RESERVATION, "'%s' 예약이 취소되었습니다."),
  RESERVATION_D1(NotificationTargetType.RESERVATION, "'%s' 예약 체크인이 하루 남았습니다."),
  CAMP_OWNER_APPROVED(NotificationTargetType.CAMP_OWNER_APPLICATION, "캠핑업체 전환 신청이 승인되었습니다."),
  CAMP_OWNER_REJECTED(NotificationTargetType.CAMP_OWNER_APPLICATION, "캠핑업체 전환 신청이 반려되었습니다.");

  private final NotificationTargetType targetType;
  private final String template;

  /** 템플릿에 {@code %s} 자리표시자가 있는지. 있으면 {@link #render(String)} 에 인자가 필수다. */
  private final boolean hasPlaceholder;

  NotificationType(NotificationTargetType targetType, String template) {
    this.targetType = targetType;
    this.template = template;
    this.hasPlaceholder = template.contains("%s");
  }

  /**
   * 메시지를 완성한다.
   *
   * <ul>
   *   <li>자리표시자가 있는 타입(예: {@code RESERVATION_*}): {@code arg} 가 <b>필수</b>다. {@code null} 이면 {@code
   *       %s} 가 그대로 남아 DB·SSE 로 새어나가므로 예외를 던진다.
   *   <li>자리표시자가 없는 타입(예: {@code CAMP_OWNER_*}): {@code arg} 는 무시되고 템플릿을 그대로 쓴다.
   * </ul>
   *
   * @throws IllegalArgumentException 자리표시자가 있는 타입인데 {@code arg} 가 {@code null} 인 경우
   */
  public String render(String arg) {
    if (!hasPlaceholder) {
      return template;
    }
    if (arg == null) {
      throw new IllegalArgumentException("알림 종류 " + name() + " 는 메시지 인자가 필요합니다.");
    }
    return String.format(template, arg);
  }
}
