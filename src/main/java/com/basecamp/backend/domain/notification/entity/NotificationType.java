package com.basecamp.backend.domain.notification.entity;

import lombok.Getter;

/**
 * 알림 종류. {@code notifications.type} 컬럼(VARCHAR)에 문자열로 저장된다(#92).
 *
 * <p>각 상수는 (1) 사용자에게 보여줄 메시지 템플릿과 (2) 알림이 가리키는 대상 종류({@link NotificationTargetType})를
 * 가진다. {@code %s} 자리에는 캠핑장 이름 등 동적 값이 들어가며, 자리표시자가 없는 템플릿(업체 전환 결과)은 인자를
 * 무시한다. 문구와 대상 종류를 한곳에 모아 두어 발생 지점(예약/관리자 도메인)이 이를 알 필요가 없게 한다.</p>
 */
@Getter
public enum NotificationType {

	RESERVATION_CONFIRMED(NotificationTargetType.RESERVATION, "'%s' 예약이 확정되었습니다."),
	RESERVATION_REJECTED(NotificationTargetType.RESERVATION, "'%s' 예약이 거절되었습니다."),
	RESERVATION_D1(NotificationTargetType.RESERVATION, "'%s' 예약 체크인이 하루 남았습니다."),
	CAMP_OWNER_APPROVED(NotificationTargetType.CAMP_OWNER_APPLICATION, "캠핑업체 전환 신청이 승인되었습니다."),
	CAMP_OWNER_REJECTED(NotificationTargetType.CAMP_OWNER_APPLICATION, "캠핑업체 전환 신청이 반려되었습니다.");

	private final NotificationTargetType targetType;
	private final String template;

	NotificationType(NotificationTargetType targetType, String template) {
		this.targetType = targetType;
		this.template = template;
	}

	/**
	 * 메시지를 완성한다. {@code arg} 가 {@code null} 이면 템플릿을 그대로 쓴다
	 * (자리표시자 없는 타입은 인자 없이 호출된다).
	 */
	public String render(String arg) {
		return arg == null ? template : String.format(template, arg);
	}
}
