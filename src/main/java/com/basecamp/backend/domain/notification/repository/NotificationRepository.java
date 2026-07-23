package com.basecamp.backend.domain.notification.repository;

import com.basecamp.backend.domain.notification.entity.Notification;
import com.basecamp.backend.domain.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  /** 내 알림 전체(최신순은 Pageable 의 sort 로 지정). */
  Page<Notification> findByUserId(Long userId, Pageable pageable);

  /** 읽음/안읽음으로 필터링한 내 알림. */
  Page<Notification> findByUserIdAndIsRead(Long userId, boolean isRead, Pageable pageable);

  /** 안읽은 알림 개수(뱃지 표시용). */
  long countByUserIdAndIsReadFalse(Long userId);

  /**
   * 같은 (수신자, 종류, 대상)의 알림이 이미 있는지. 발송 멱등성 확보용 — 스케줄러 재실행 시 유니크 제약 위반 예외 없이 조용히 스킵하기 위한 fast-path
   * 다({@code uq_notif_user_type_target}).
   */
  boolean existsByUserIdAndTypeAndTargetId(Long userId, NotificationType type, Long targetId);

  /** 내 안읽은 알림을 한 번의 UPDATE 로 전부 읽음 처리한다. 개별 조회 없이 처리해 N 번의 왕복을 피한다. */
  @Modifying(clearAutomatically = true)
  @Query("update Notification n set n.isRead = true where n.userId = :userId and n.isRead = false")
  int markAllAsReadByUserId(@Param("userId") Long userId);
}
