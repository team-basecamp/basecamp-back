package com.basecamp.backend.domain.notification.entity;

/**
 * 알림이 가리키는 대상 종류. {@code notifications.target_type} 컬럼(VARCHAR)에 문자열로 저장된다(V2, B안).
 *
 * <p>{@code target_id} 와 짝을 이뤄 "이 알림이 어떤 리소스에 관한 것인가"를 나타낸다. FK 없이 애플리케이션이 정합성을 책임지므로, 여러
 * 도메인(예약/신청/…)을 한 컬럼으로 가리킬 수 있다. 프론트가 알림 클릭 시 어느 상세 화면으로 이동할지 결정하는 데 쓴다.
 */
public enum NotificationTargetType {

  /** 예약. {@code target_id} 는 reservation_id. */
  RESERVATION,

  /** 캠핑업체 전환 신청. {@code target_id} 는 application_id. */
  CAMP_OWNER_APPLICATION
}
