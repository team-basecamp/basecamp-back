package com.basecamp.backend.domain.notification.service;

import com.basecamp.backend.domain.notification.entity.Notification;
import com.basecamp.backend.domain.notification.entity.NotificationType;
import com.basecamp.backend.domain.notification.repository.NotificationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 저장을 <b>독립 트랜잭션</b>({@code REQUIRES_NEW})에서 수행한다(#92).
 *
 * <p>저장을 {@link NotificationService} 에서 떼어내 별도 빈으로 둔 이유는 두 가지다.
 *
 * <ol>
 *   <li><b>호출 시점.</b> 트리거 커밋 이후({@code AFTER_COMMIT}) 리스너나 스케줄러처럼, 진행 중인 트랜잭션이 없거나 완료 중인 컨텍스트에서
 *       호출된다. 저장에는 새 트랜잭션이 필요하다.
 *   <li><b>중복 삼키기.</b> 유니크 제약({@code uq_notif_user_type_target}, V22) 경합에서 지면 {@link
 *       DataIntegrityViolationException} 이 나고 이 트랜잭션은 롤백된다. 호출자가 그 예외를 잡아 정상 no-op 으로 끝내려면 실패하는 저장이
 *       <b>별도 트랜잭션 경계 안</b>에 있어야 한다 — 같은 트랜잭션 안에서 잡으면 Hibernate 가 이미 rollback-only 로 표시해 둔 탓에 커밋
 *       시점에 {@code UnexpectedRollbackException} 이 난다.
 * </ol>
 *
 * <p>이 메서드가 정상 반환했다는 것은 저장이 <b>커밋됐다</b>는 뜻이다. 호출자는 반환 이후에 push 하면 되므로 유령 알림(커밋 전 전송)이 생기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationWriter {

  private final NotificationRepository notificationRepository;

  /**
   * 같은 {@code (userId, type, targetId)} 알림이 없으면 저장한다.
   *
   * <p>존재 검사는 스케줄러 재실행처럼 <b>순차적인</b> 중복을 예외 없이 걸러내는 fast-path 다. 인스턴스 간 <b>동시</b> 삽입 경합은 이 검사를 함께
   * 통과할 수 있으며, 그때의 최종 방어선은 DB 유니크 제약이다.
   *
   * @return 저장된 알림. 이미 같은 알림이 있으면 {@link Optional#empty()}
   * @throws DataIntegrityViolationException 동시 삽입 경합으로 유니크 제약을 위반한 경우(호출자가 no-op 처리)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<Notification> saveIfAbsent(
      Long userId, NotificationType type, Long targetId, String arg) {
    // target 이 없는 알림(전체 공지 등)은 중복 개념이 없어 검사에서 제외한다.
    if (targetId != null
        && notificationRepository.existsByUserIdAndTypeAndTargetId(userId, type, targetId)) {
      return Optional.empty();
    }
    return Optional.of(
        notificationRepository.save(Notification.create(userId, type, type.render(arg), targetId)));
  }
}
