package com.basecamp.backend.domain.notification.event;

import com.basecamp.backend.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link NotificationEvent} 를 트리거 트랜잭션 커밋 이후에 받아 알림을 저장·push 한다(#92).
 *
 * <p>{@code AFTER_COMMIT} 이라 트리거(예약 확정/거절, 업체 승인/반려)가 <b>확정된 뒤에만</b> 알림이 나간다. 알림 발생 중 오류가 나도 이미 커밋된
 * 트리거에는 영향이 없어야 하므로 예외를 삼키고 로그만 남긴다 — 알림은 best-effort 다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

  private final NotificationService notificationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(NotificationEvent event) {
    try {
      notificationService.send(event.userId(), event.type(), event.targetId(), event.arg());
    } catch (Exception e) {
      log.warn(
          "알림 발생 실패: userId={}, type={}, targetId={}",
          event.userId(),
          event.type(),
          event.targetId(),
          e);
    }
  }
}
