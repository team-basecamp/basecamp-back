package com.basecamp.backend.domain.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.basecamp.backend.domain.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/** 내 알림 전체(최신순은 Pageable 의 sort 로 지정). */
	Page<Notification> findByUserId(Long userId, Pageable pageable);

	/** 읽음/안읽음으로 필터링한 내 알림. */
	Page<Notification> findByUserIdAndIsRead(Long userId, boolean isRead, Pageable pageable);

	/** 안읽은 알림 개수(뱃지 표시용). */
	long countByUserIdAndIsReadFalse(Long userId);

	/**
	 * 내 안읽은 알림을 한 번의 UPDATE 로 전부 읽음 처리한다.
	 * 개별 조회 없이 처리해 N 번의 왕복을 피한다.
	 */
	@Modifying(clearAutomatically = true)
	@Query("update Notification n set n.isRead = true where n.userId = :userId and n.isRead = false")
	int markAllAsReadByUserId(@Param("userId") Long userId);
}
