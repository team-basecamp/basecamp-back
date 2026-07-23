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
 * <p>한 사용자가 여러 기기/탭으로 접속할 수 있으므로 {@code userId → List<SseEmitter>} 로 관리한다. 저장소가 프로세스 메모리라 <b>단일
 * 인스턴스 전제</b>다. 스케일아웃 시에는 다른 인스턴스에 붙은 연결로는 push 가 닿지 않으므로 Redis Pub/Sub 로 인스턴스 간 fan-out 을 얹어야
 * 한다(이슈의 확장 항목).
 *
 * <p>동시 접근을 위해 두 층을 함께 쓴다. (1) <b>맵 갱신은 키 단위 원자 연산</b>({@code compute}/{@code computeIfPresent})으로
 * 묶는다. 리스트에 add/remove 하는 것과 "마지막 연결이 사라진 키를 제거"하는 것을 remapping 함수 안에서 한 번에 처리해, 같은 {@code userId}
 * 에서 등록과 정리가 겹쳐도 emitter 가 맵에서 떨어져 나간 리스트에 남지 않게 한다. (2) 리스트는 {@link CopyOnWriteArrayList} 라, push
 * 가 {@link #findByUserId} 로 받은 리스트를 순회하는 도중 다른 스레드가 add/remove 해도 순회가 깨지지 않는다.
 */
@Repository
public class SseEmitterRepository {

  private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

  public SseEmitter save(Long userId, SseEmitter emitter) {
    // add 까지 remapping 함수 안에서 수행해, 리스트 생성과 등록이 하나의 원자 연산이 되게 한다.
    emittersByUser.compute(
        userId,
        (key, emitters) -> {
          if (emitters == null) {
            emitters = new CopyOnWriteArrayList<>();
          }
          emitters.add(emitter);
          return emitters;
        });
    return emitter;
  }

  public List<SseEmitter> findByUserId(Long userId) {
    return emittersByUser.getOrDefault(userId, List.of());
  }

  public void remove(Long userId, SseEmitter emitter) {
    // remove 와 "빈 리스트면 키 삭제" 를 한 원자 연산으로 묶는다. null 을 반환하면 매핑이 제거된다.
    emittersByUser.computeIfPresent(
        userId,
        (key, emitters) -> {
          emitters.remove(emitter);
          return emitters.isEmpty() ? null : emitters;
        });
  }
}
