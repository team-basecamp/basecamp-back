package com.basecamp.backend.domain.notification.scheduler;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.basecamp.backend.domain.notification.entity.NotificationType;
import com.basecamp.backend.domain.notification.service.NotificationService;
import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.basecamp.backend.domain.reservation.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예약 체크인 D-1 알림 스케줄러(#92).
 *
 * <p>매일 오전 10시에 <b>다음 날 체크인하는 확정({@code RESERVED}) 예약</b>을 찾아 예약자에게 알림을 보낸다.
 * 한 건의 실패가 나머지를 막지 않도록 개별 예약마다 예외를 잡는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationReminderScheduler {

	private final ReservationRepository reservationRepository;
	private final NotificationService notificationService;

	@Scheduled(cron = "0 0 10 * * *")
	public void sendCheckInReminders() {
		LocalDate tomorrow = LocalDate.now().plusDays(1);

		List<Reservation> targets =
				reservationRepository.findAllForCheckInReminder(tomorrow, ReservationStatus.RESERVED);
		if (targets.isEmpty()) {
			return;
		}

		int sent = 0;
		for (Reservation reservation : targets) {
			try {
				notificationService.send(
						reservation.getUser().getId(),
						NotificationType.RESERVATION_D1,
						reservation.getId(),
						reservation.getCamp().getFacltNm());
				sent++;
			} catch (Exception e) {
				log.warn("D-1 알림 발송 실패: reservationId={}", reservation.getId(), e);
			}
		}
		log.info("예약 D-1 알림 발송: 대상={}건, 성공={}건", targets.size(), sent);
	}
}
