package com.basecamp.backend.domain.notification.service;

import java.io.IOException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.notification.dto.response.NotificationResponse;
import com.basecamp.backend.domain.notification.dto.response.UnreadCountResponse;
import com.basecamp.backend.domain.notification.entity.Notification;
import com.basecamp.backend.domain.notification.entity.NotificationType;
import com.basecamp.backend.domain.notification.repository.NotificationRepository;
import com.basecamp.backend.domain.notification.repository.SseEmitterRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 저장 · 실시간 push · 조회/읽음 처리(#92).
 *
 * <p>발생(저장+push)은 {@code NotificationEventListener}(트리거 커밋 이후) 와 D-1 스케줄러가 호출한다.
 * 조회/읽음 처리는 컨트롤러가 호출한다.</p>
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

	// ── 구독(SSE) ────────────────────────────────────────────────────────

	/**
	 * 사용자의 SSE 연결을 생성·등록하고, 최초 연결 확인용 더미 이벤트를 보낸다.
	 * 더미 이벤트는 프록시가 첫 바이트를 기다리다 연결을 끊는 것을 막고, 클라이언트가 구독 성공을 알게 한다.
	 */
	public SseEmitter subscribe(Long userId) {
		SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
		emitterRepository.save(userId, emitter);

		emitter.onCompletion(() -> emitterRepository.remove(userId, emitter));
		emitter.onTimeout(() -> {
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
	 * 알림을 저장하고 접속 중인 연결로 push 한다.
	 *
	 * <p>트리거 트랜잭션의 커밋 이후({@code AFTER_COMMIT})에 호출되므로, 저장은 <b>새 트랜잭션</b>에서 이뤄져야 한다
	 * ({@code REQUIRES_NEW}). 알림 저장 실패는 트리거(예약 확정 등)를 되돌리지 않는다 — 알림은 best-effort 다.</p>
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void send(Long userId, NotificationType type, Long targetId, String arg) {
		Notification saved = notificationRepository.save(
				Notification.create(userId, type, type.render(arg), targetId));
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
	public Page<NotificationResponse> findMyNotifications(Long userId, Boolean isRead, Pageable pageable) {
		Page<Notification> notifications = (isRead == null)
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
		Notification notification = notificationRepository.findById(notificationId)
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
