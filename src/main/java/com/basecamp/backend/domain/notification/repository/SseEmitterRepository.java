package com.basecamp.backend.domain.notification.repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 연결(SseEmitter)을 사용자별로 들고 있는 인메모리 저장소(#92).
 *
 * <p>한 사용자가 여러 기기/탭으로 접속할 수 있으므로 {@code userId → List<SseEmitter>} 로 관리한다.
 * 저장소가 프로세스 메모리라 <b>단일 인스턴스 전제</b>다. 스케일아웃 시에는 다른 인스턴스에 붙은 연결로는 push 가
 * 닿지 않으므로 Redis Pub/Sub 로 인스턴스 간 fan-out 을 얹어야 한다(이슈의 확장 항목).</p>
 *
 * <p>동시 접근을 위해 {@link ConcurrentHashMap} + {@link CopyOnWriteArrayList} 를 쓴다. push 도중 다른 스레드가
 * 연결을 추가/제거해도 순회가 깨지지 않는다.</p>
 */
@Repository
public class SseEmitterRepository {

	private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

	public SseEmitter save(Long userId, SseEmitter emitter) {
		emittersByUser.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
		return emitter;
	}

	public List<SseEmitter> findByUserId(Long userId) {
		return emittersByUser.getOrDefault(userId, List.of());
	}

	public void remove(Long userId, SseEmitter emitter) {
		List<SseEmitter> emitters = emittersByUser.get(userId);
		if (emitters == null) {
			return;
		}
		emitters.remove(emitter);
		// 마지막 연결이 사라지면 유저 키도 정리해 맵이 무한정 커지지 않게 한다.
		emittersByUser.remove(userId, List.of());
	}
}
