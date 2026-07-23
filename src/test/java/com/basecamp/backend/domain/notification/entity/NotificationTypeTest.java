package com.basecamp.backend.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link NotificationType#render(String)} 의 인자 처리 규칙 단위 테스트(#92). */
class NotificationTypeTest {

  @Test
  @DisplayName("render_자리표시자타입_arg가있으면_치환한다")
  void render_자리표시자타입_치환한다() {
    assertThat(NotificationType.RESERVATION_CONFIRMED.render("해운대 오토캠핑장"))
        .isEqualTo("'해운대 오토캠핑장' 예약이 확정되었습니다.");
  }

  @Test
  @DisplayName("render_자리표시자타입_argNull이면_예외를던진다")
  void render_자리표시자타입_argNull이면_예외() {
    // %s 가 그대로 DB·SSE 로 새어나가지 않도록 막는다.
    assertThatThrownBy(() -> NotificationType.RESERVATION_CONFIRMED.render(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("render_자리표시자없는타입_argNull이면_템플릿을그대로반환한다")
  void render_자리표시자없는타입_argNull_템플릿그대로() {
    assertThat(NotificationType.CAMP_OWNER_APPROVED.render(null)).isEqualTo("캠핑업체 전환 신청이 승인되었습니다.");
  }
}
