package com.basecamp.backend.domain.notification.service;

import java.io.IOException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
	 *
	 * <p><b>멱등 보장:</b> 같은 {@code (userId, type, targetId)} 알림이 이미 있으면 저장도 push 도 하지 않는다.
	 * 스케줄러 재실행이나 다중 인스턴스에서 D-1 등 알림이 중복 발송되는 것을 막는다. 아래 존재 검사는 재실행을
	 * 조용히 걸러내는 fast-path 이고, 인스턴스 간 동시 삽입 경합의 <b>최종 방어선은 DB 유니크 제약</b>
	 * {@code uq_notif_user_type_target}(V22) 이다 — 경합에서 진 쪽의 저장은 무결성 예외로 실패하고 push 되지 않는다.</p>
	 *
	 * <p><b>push 는 커밋 이후로 미룬다.</b> 저장과 같은 트랜잭션 안에서 push 하면, 커밋 전에 클라이언트가 알림을 받은 뒤
	 * 커밋이 실패(유니크 제약 경합 등)했을 때 <b>유령 알림</b>이 남는다. 그래서 {@link TransactionSynchronization#afterCommit()}
	 * 에 push 를 등록해, 저장이 실제로 확정된 뒤에만 전송한다. 트랜잭션이 없는 호출(테스트 등)에서는 즉시 전송한다.</p>
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void send(Long userId, NotificationType type, Long targetId, String arg) {
		// 이미 보낸 알림이면 재발송하지 않는다(멱등). target 이 없는 알림(공지 등)은 중복 개념이 없어 검사에서 제외.
		if (targetId != null && notificationRepository.existsByUserIdAndTypeAndTargetId(userId, type, targetId)) {
			return;
		}
		Notification saved = notificationRepository.save(
				Notification.create(userId, type, type.render(arg), targetId));
		pushAfterCommit(userId, NotificationResponse.from(saved));
	}

	/**
	 * 현재 트랜잭션이 커밋된 뒤에 push 하도록 등록한다. 트랜잭션 동기화가 없으면(테스트 등) 즉시 push 한다.
	 * 커밋되지 않고 롤백되면 {@code afterCommit} 이 호출되지 않아 유령 알림이 생기지 않는다.
	 */
	private void pushAfterCommit(Long userId, NotificationResponse payload) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			push(userId, payload);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				push(userId, payload);
			}
		});
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
