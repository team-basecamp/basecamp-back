package com.basecamp.backend.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.notification.entity.Notification;
import com.basecamp.backend.domain.notification.entity.NotificationType;
import com.basecamp.backend.domain.notification.repository.NotificationRepository;
import com.basecamp.backend.domain.notification.repository.SseEmitterRepository;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 알림 저장·push·조회/읽음 처리 단위 테스트(#92).
 *
 * <p>핵심은 "저장하고 접속 중인 연결로 push 한다", "본인 알림만 읽음 처리한다" 두 가지다. SSE 연결/DB 는 목킹하고 서비스 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  private static final Long USER_ID = 7L;
  private static final Long OTHER_USER_ID = 99L;
  private static final Long NOTIFICATION_ID = 1L;

  @Mock private NotificationRepository notificationRepository;

  @Mock private SseEmitterRepository emitterRepository;

  @Mock private NotificationWriter notificationWriter;

  @InjectMocks private NotificationService notificationService;

  private Notification notificationOf(Long ownerId) {
    return Notification.create(ownerId, NotificationType.RESERVATION_CONFIRMED, "메시지", 42L);
  }

  // ── send ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("send_알림을저장하고_구독중인연결로push한다")
  void send_알림을저장하고_push한다() throws IOException {
    // given: 유저가 하나의 SSE 연결을 열고 있다.
    SseEmitter emitter = org.mockito.Mockito.mock(SseEmitter.class);
    given(emitterRepository.findByUserId(USER_ID)).willReturn(List.of(emitter));
    given(
            notificationWriter.saveIfAbsent(
                USER_ID, NotificationType.RESERVATION_CONFIRMED, 42L, "해운대 오토캠핑장"))
        .willReturn(Optional.of(notificationOf(USER_ID)));

    // when
    notificationService.send(USER_ID, NotificationType.RESERVATION_CONFIRMED, 42L, "해운대 오토캠핑장");

    // then: 저장이 커밋된(=saveIfAbsent 가 정상 반환한) 뒤 접속 중인 연결로 push 된다.
    verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  @DisplayName("send_끊긴연결이면_push실패를삼키고_연결을정리한다")
  void send_끊긴연결이면_정리한다() throws IOException {
    // given: push 도중 연결이 끊겨 IOException 이 난다.
    SseEmitter deadEmitter = org.mockito.Mockito.mock(SseEmitter.class);
    given(emitterRepository.findByUserId(USER_ID)).willReturn(List.of(deadEmitter));
    given(
            notificationWriter.saveIfAbsent(
                USER_ID, NotificationType.RESERVATION_REJECTED, 42L, "해운대 오토캠핑장"))
        .willReturn(Optional.of(notificationOf(USER_ID)));
    org.mockito.BDDMockito.willThrow(new IOException("broken pipe"))
        .given(deadEmitter)
        .send(any(SseEmitter.SseEventBuilder.class));

    // when: 예외가 밖으로 전파되지 않는다(알림은 best-effort).
    notificationService.send(USER_ID, NotificationType.RESERVATION_REJECTED, 42L, "해운대 오토캠핑장");

    // then: 끊긴 연결은 저장소에서 제거된다.
    verify(emitterRepository).remove(USER_ID, deadEmitter);
  }

  @Test
  @DisplayName("send_같은알림이이미있으면_push하지않는다(멱등)")
  void send_이미같은알림이있으면_스킵한다() {
    // given: 존재 검사에 걸려 저장이 생략된 상황(스케줄러 재실행 등 순차 중복).
    given(
            notificationWriter.saveIfAbsent(
                USER_ID, NotificationType.RESERVATION_D1, 42L, "해운대 오토캠핑장"))
        .willReturn(Optional.empty());

    // when
    notificationService.send(USER_ID, NotificationType.RESERVATION_D1, 42L, "해운대 오토캠핑장");

    // then: 중복 전송이 없다.
    verify(emitterRepository, never()).findByUserId(any());
  }

  @Test
  @DisplayName("send_동시삽입_유니크충돌_예외없이_noop으로끝나고_push하지않는다")
  void send_유니크충돌_예외없이_noop으로끝난다() {
    // given: 다중 인스턴스가 존재 검사를 함께 통과한 뒤, insert 에서 uq_notif_user_type_target 에 걸린 경합.
    //        진 쪽은 DataIntegrityViolationException 을 받는다(= 이미 다른 쪽이 1회 저장했다는 뜻).
    given(
            notificationWriter.saveIfAbsent(
                USER_ID, NotificationType.RESERVATION_D1, 42L, "해운대 오토캠핑장"))
        .willThrow(new DataIntegrityViolationException("uq_notif_user_type_target"));

    // when & then: 중복은 실패가 아니라 정상 no-op 이므로 예외가 전파되지 않는다.
    assertThatCode(
            () ->
                notificationService.send(
                    USER_ID, NotificationType.RESERVATION_D1, 42L, "해운대 오토캠핑장"))
        .doesNotThrowAnyException();

    // 저장에 성공한 쪽만 push 한다. 진 쪽이 push 하면 중복 전송이 되므로 하지 않는다.
    verify(emitterRepository, never()).findByUserId(any());
  }

  // ── 읽음 처리 ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("markAsRead_본인알림_읽음처리된다")
  void markAsRead_본인알림_읽음처리된다() {
    // given
    Notification notification = notificationOf(USER_ID);
    given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));

    // when
    notificationService.markAsRead(NOTIFICATION_ID, USER_ID);

    // then
    assertThat(notification.isRead()).isTrue();
  }

  @Test
  @DisplayName("markAsRead_없는알림_N001을던진다")
  void markAsRead_없는알림_N001을던진다() {
    // given
    given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> notificationService.markAsRead(NOTIFICATION_ID, USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
  }

  @Test
  @DisplayName("markAsRead_타인알림_A004를던지고_읽음처리하지않는다")
  void markAsRead_타인알림_A004를던진다() {
    // given: 다른 사용자의 알림.
    Notification notification = notificationOf(OTHER_USER_ID);
    given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));

    // when & then
    assertThatThrownBy(() -> notificationService.markAsRead(NOTIFICATION_ID, USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    assertThat(notification.isRead()).isFalse();
  }

  @Test
  @DisplayName("markAllAsRead_리포지토리의일괄읽음처리를호출한다")
  void markAllAsRead_일괄처리를호출한다() {
    // when
    notificationService.markAllAsRead(USER_ID);

    // then
    verify(notificationRepository).markAllAsReadByUserId(USER_ID);
  }

  // ── 조회 ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("getUnreadCount_안읽은개수를반환한다")
  void getUnreadCount_안읽은개수를반환한다() {
    // given
    given(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).willReturn(3L);

    // when & then
    assertThat(notificationService.getUnreadCount(USER_ID).count()).isEqualTo(3L);
  }

  @Test
  @DisplayName("findMyNotifications_isRead없으면_전체조회한다")
  void findMyNotifications_isRead없으면_전체조회한다() {
    // given
    given(notificationRepository.findByUserId(eq(USER_ID), any())).willReturn(Page.empty());

    // when
    notificationService.findMyNotifications(USER_ID, null, PageRequest.of(0, 10));

    // then: 필터 쿼리가 아닌 전체 조회 쿼리를 쓴다.
    verify(notificationRepository).findByUserId(eq(USER_ID), any());
    verify(notificationRepository, never()).findByUserIdAndIsRead(anyLong(), anyBoolean(), any());
  }

  @Test
  @DisplayName("findMyNotifications_isRead주어지면_필터조회한다")
  void findMyNotifications_isRead주어지면_필터조회한다() {
    // given
    given(notificationRepository.findByUserIdAndIsRead(eq(USER_ID), eq(false), any()))
        .willReturn(Page.empty());

    // when
    notificationService.findMyNotifications(USER_ID, false, PageRequest.of(0, 10));

    // then
    verify(notificationRepository).findByUserIdAndIsRead(eq(USER_ID), eq(false), any());
    verify(notificationRepository, never()).findByUserId(anyLong(), any());
  }

  // ── 구독 ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("subscribe_연결을저장소에등록하고_emitter를반환한다")
  void subscribe_연결을등록하고_emitter를반환한다() {
    // when
    SseEmitter emitter = notificationService.subscribe(USER_ID);

    // then
    assertThat(emitter).isNotNull();
    verify(emitterRepository).save(eq(USER_ID), any(SseEmitter.class));
  }
}
