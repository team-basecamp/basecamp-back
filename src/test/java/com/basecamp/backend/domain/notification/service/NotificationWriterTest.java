package com.basecamp.backend.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.basecamp.backend.domain.notification.entity.Notification;
import com.basecamp.backend.domain.notification.entity.NotificationTargetType;
import com.basecamp.backend.domain.notification.entity.NotificationType;
import com.basecamp.backend.domain.notification.repository.NotificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 알림 저장(멱등) 단위 테스트(#92).
 *
 * <p>동시 삽입 경합 시의 유니크 제약 위반은 DB 가 강제하므로 여기서 재현하지 않는다. 이 테스트는 순차 중복을 걸러내는 존재 검사와, 저장되는 내용이 알림 종류에서
 * 올바르게 파생되는지를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationWriterTest {

  private static final Long USER_ID = 7L;
  private static final Long TARGET_ID = 42L;

  @Mock private NotificationRepository notificationRepository;

  @InjectMocks private NotificationWriter notificationWriter;

  @Test
  @DisplayName("saveIfAbsent_같은알림이없으면_종류에서파생된값으로저장한다")
  void saveIfAbsent_없으면_저장한다() {
    // given
    given(
            notificationRepository.existsByUserIdAndTypeAndTargetId(
                USER_ID, NotificationType.RESERVATION_CONFIRMED, TARGET_ID))
        .willReturn(false);
    given(notificationRepository.save(any(Notification.class)))
        .willAnswer(inv -> inv.getArgument(0));

    // when
    Optional<Notification> result =
        notificationWriter.saveIfAbsent(
            USER_ID, NotificationType.RESERVATION_CONFIRMED, TARGET_ID, "해운대 오토캠핑장");

    // then: 대상 종류·메시지는 알림 종류에서 파생되고, 읽지 않음 상태로 시작한다.
    assertThat(result).isPresent();
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(captor.capture());
    Notification saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getType()).isEqualTo(NotificationType.RESERVATION_CONFIRMED);
    assertThat(saved.getTargetType()).isEqualTo(NotificationTargetType.RESERVATION);
    assertThat(saved.getTargetId()).isEqualTo(TARGET_ID);
    assertThat(saved.getMessage()).isEqualTo("'해운대 오토캠핑장' 예약이 확정되었습니다.");
    assertThat(saved.isRead()).isFalse();
  }

  @Test
  @DisplayName("saveIfAbsent_같은알림이이미있으면_저장하지않고_empty를반환한다")
  void saveIfAbsent_이미있으면_저장하지않는다() {
    // given: 스케줄러 재실행 등 순차 중복.
    given(
            notificationRepository.existsByUserIdAndTypeAndTargetId(
                USER_ID, NotificationType.RESERVATION_D1, TARGET_ID))
        .willReturn(true);

    // when
    Optional<Notification> result =
        notificationWriter.saveIfAbsent(
            USER_ID, NotificationType.RESERVATION_D1, TARGET_ID, "해운대 오토캠핑장");

    // then
    assertThat(result).isEmpty();
    verify(notificationRepository, never()).save(any());
  }

  @Test
  @DisplayName("saveIfAbsent_target이없는알림은_존재검사없이_저장한다")
  void saveIfAbsent_target이null이면_검사를건너뛴다() {
    // given: 전체 공지처럼 대상이 없는 알림은 중복 개념이 없다.
    given(notificationRepository.save(any(Notification.class)))
        .willAnswer(inv -> inv.getArgument(0));

    // when
    Optional<Notification> result =
        notificationWriter.saveIfAbsent(USER_ID, NotificationType.CAMP_OWNER_APPROVED, null, null);

    // then
    assertThat(result).isPresent();
    verify(notificationRepository, never()).existsByUserIdAndTypeAndTargetId(any(), any(), any());
    verify(notificationRepository).save(any(Notification.class));
  }
}
