package com.basecamp.backend.domain.notification.event;

import com.basecamp.backend.domain.notification.entity.NotificationType;

/**
 * 알림 발생 이벤트. 예약/관리자 도메인이 상태를 바꾼 뒤 발행하고, {@code NotificationEventListener} 가 트랜잭션 커밋 이후에 받아 저장·push
 * 한다(#92).
 *
 * <p>발행 도메인이 notification 도메인을 직접 의존하지 않게 하고, 트리거 트랜잭션이 <b>커밋된 뒤에만</b> 알림이 나가도록 분리한다. 롤백되면 이벤트 리스너가
 * 호출되지 않아 "취소된 동작에 대한 알림"이 발생하지 않는다.
 *
 * @param userId 수신자 ID
 * @param type 알림 종류
 * @param targetId 대상 엔티티 ID(예약/신청 등, 상세 이동용, 없으면 {@code null})
 * @param arg 메시지 템플릿에 채울 동적 값(예: 캠핑장 이름). 자리표시자 없는 타입은 {@code null}
 */
public record NotificationEvent(Long userId, NotificationType type, Long targetId, String arg) {

  public static NotificationEvent of(
      Long userId, NotificationType type, Long targetId, String arg) {
    return new NotificationEvent(userId, type, targetId, arg);
  }
}
