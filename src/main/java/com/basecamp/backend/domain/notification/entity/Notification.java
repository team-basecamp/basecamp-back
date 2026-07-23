package com.basecamp.backend.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 사용자 알림. V1 에서 생성된 {@code notifications} 테이블과 매핑된다(#92, 스키마 변경 없음).
 *
 * <p>{@code user_id} 는 감사 목적의 참조라 연관 매핑 대신 식별자만 들고 있다({@code CampOwnerApplication} 과 같은 방식). 알림을 읽을
 * 때 {@code User} 를 함께 조회할 이유가 없다. {@code target_type} + {@code target_id} 는 알림이 가리키는 대상(예약/신청 등)으로,
 * 프론트가 상세 화면으로 이동할 때 쓴다(V2 의 B안, FK 없음 — 어떤 도메인이든 가리킬 수 있게).
 */
@Getter
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "notification_id")
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 50)
  private NotificationType type;

  @Column(name = "message", nullable = false, length = 300)
  private String message;

  /** 알림이 가리키는 대상 종류(V2). */
  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", length = 30)
  private NotificationTargetType targetType;

  /** 대상 엔티티 ID(예약/신청 등). FK 가 아닌 단순 참조라 없을 수도 있다(전체 공지 등). */
  @Column(name = "target_id")
  private Long targetId;

  @Column(name = "is_read", nullable = false)
  private boolean isRead;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder(access = AccessLevel.PRIVATE)
  private Notification(Long userId, NotificationType type, String message, Long targetId) {
    this.userId = userId;
    this.type = type;
    this.message = message;
    this.targetType = type.getTargetType(); // 대상 종류는 알림 종류에서 파생된다
    this.targetId = targetId;
    this.isRead = false;
  }

  /** 새 알림을 생성한다. 항상 읽지 않음(false) 상태로 시작한다. */
  public static Notification create(
      Long userId, NotificationType type, String message, Long targetId) {
    return Notification.builder()
        .userId(userId)
        .type(type)
        .message(message)
        .targetId(targetId)
        .build();
  }

  /** 읽음 처리. 이미 읽은 알림을 다시 호출해도 안전하다(멱등). */
  public void markAsRead() {
    this.isRead = true;
  }

  /** 이 알림이 주어진 사용자의 것인지 확인한다. */
  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }
}
