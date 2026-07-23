package com.basecamp.backend.domain.notification.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.notification.dto.response.NotificationResponse;
import com.basecamp.backend.domain.notification.dto.response.UnreadCountResponse;
import com.basecamp.backend.domain.notification.entity.Notification;
import com.basecamp.backend.domain.notification.entity.NotificationType;
import com.basecamp.backend.domain.notification.repository.NotificationRepository;
import com.basecamp.backend.domain.notification.repository.SseEmitterRepository;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 알림 저장 · 실시간 push · 조회/읽음 처리(#92).
 *
 * <p>발생(저장+push)은 {@code NotificationEventListener}(트리거 커밋 이후) 와 D-1 스케줄러가 호출한다. 조회/읽음 처리는 컨트롤러가
 * 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  /** SSE 연결 유지 시간(60분). 이 시간이 지나면 클라이언트가 재연결한다. */
  private static final long SSE_TIMEOUT_MILLIS = 60L * 60 * 1000;

  private static final String EVENT_CONNECT = "connect";
  private static final String EVENT_NOTIFICATION = "notification";

  private final NotificationRepository notificationRepository;
  private final SseEmitterRepository emitterRepository;
  private final NotificationWriter notificationWriter;

  // ── 구독(SSE) ────────────────────────────────────────────────────────

  /**
   * 사용자의 SSE 연결을 생성·등록하고, 최초 연결 확인용 더미 이벤트를 보낸다. 더미 이벤트는 프록시가 첫 바이트를 기다리다 연결을 끊는 것을 막고, 클라이언트가 구독
   * 성공을 알게 한다.
   */
  public SseEmitter subscribe(Long userId) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
    emitterRepository.save(userId, emitter);

    emitter.onCompletion(() -> emitterRepository.remove(userId, emitter));
    emitter.onTimeout(
        () -> {
          emitter.complete();
          emitterRepository.remove(userId, emitter);
        });
    emitter.onError(e -> emitterRepository.remove(userId, emitter));

    try {
      emitter.send(SseEmitter.event().name(EVENT_CONNECT).data("connected"));
    } catch (IOException e) {
      emitterRepository.remove(userId, emitter);
    }
    return emitter;
  }

  // ── 발생(저장 + push) ─────────────────────────────────────────────────

  /**
   * 알림을 저장하고 접속 중인 연결로 push 한다. 알림은 best-effort 라 실패해도 트리거(예약 확정 등)를 되돌리지 않는다.
   *
   * <p><b>멱등 보장.</b> 같은 {@code (userId, type, targetId)} 알림은 한 번만 저장·전송된다. 스케줄러 재실행처럼 순차적인 중복은
   * {@link NotificationWriter#saveIfAbsent} 의 존재 검사가 걸러내고({@link Optional#empty()} 반환), 다중 인스턴스의
   * <b>동시</b> 삽입 경합은 DB 유니크 제약 {@code uq_notif_user_type_target}(V22)이 막는다. 경합에서 진 쪽은 {@link
   * DataIntegrityViolationException} 을 받지만, 이는 "이미 다른 쪽이 저장했다"는 뜻일 뿐 실패가 아니므로 <b>삼키고 정상 종료</b>한다(중복
   * insert 를 요청 실패로 전파하지 않는다).
   *
   * <p><b>push 는 저장이 커밋된 뒤에만 한다.</b> {@code saveIfAbsent} 는 독립 트랜잭션({@code REQUIRES_NEW})이라 정상 반환 =
   * 커밋 완료다. 롤백되면 예외로 빠져 push 에 도달하지 않으므로 유령 알림이 생기지 않는다.
   */
  public void send(Long userId, NotificationType type, Long targetId, String arg) {
    Notification saved;
    try {
      Optional<Notification> result = notificationWriter.saveIfAbsent(userId, type, targetId, arg);
      if (result.isEmpty()) {
        return; // 이미 보낸 알림 → no-op
      }
      saved = result.get();
    } catch (DataIntegrityViolationException e) {
      // 동시 삽입 경합에서 진 경우. 다른 쪽이 이미 저장했으므로 중복은 정상 no-op 이다.
      log.debug("중복 알림 무시(멱등). userId={}, type={}, targetId={}", userId, type, targetId);
      return;
    }
    push(userId, NotificationResponse.from(saved));
  }

  private void push(Long userId, NotificationResponse payload) {
    for (SseEmitter emitter : emitterRepository.findByUserId(userId)) {
      try {
        emitter.send(SseEmitter.event().name(EVENT_NOTIFICATION).data(payload));
      } catch (IOException | IllegalStateException e) {
        // 끊긴 연결. push 실패가 저장까지 되돌리면 안 되므로 삼키고 정리만 한다.
        emitterRepository.remove(userId, emitter);
      }
    }
  }

  // ── 조회 / 읽음 처리 ──────────────────────────────────────────────────

  /** 내 알림 목록. {@code isRead} 가 주어지면 읽음/안읽음으로 필터링한다. */
  @Transactional(readOnly = true)
  public Page<NotificationResponse> findMyNotifications(
      Long userId, Boolean isRead, Pageable pageable) {
    Page<Notification> notifications =
        (isRead == null)
            ? notificationRepository.findByUserId(userId, pageable)
            : notificationRepository.findByUserIdAndIsRead(userId, isRead, pageable);
    return notifications.map(NotificationResponse::from);
  }

  /** 안읽은 알림 개수. */
  @Transactional(readOnly = true)
  public UnreadCountResponse getUnreadCount(Long userId) {
    return UnreadCountResponse.of(notificationRepository.countByUserIdAndIsReadFalse(userId));
  }

  /** 개별 알림 읽음 처리. 본인 알림만 가능하며, 이미 읽었어도 정상 처리된다(멱등). */
  @Transactional
  public void markAsRead(Long notificationId, Long userId) {
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
    if (!notification.isOwnedBy(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    notification.markAsRead();
  }

  /** 내 안읽은 알림 전체 읽음 처리. */
  @Transactional
  public void markAllAsRead(Long userId) {
    notificationRepository.markAllAsReadByUserId(userId);
  }
}
