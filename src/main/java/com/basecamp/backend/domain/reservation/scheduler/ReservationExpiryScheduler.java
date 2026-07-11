package com.basecamp.backend.domain.reservation.scheduler;

import com.basecamp.backend.domain.payment.entity.PaymentStatus;
import com.basecamp.backend.domain.payment.repository.PaymentRepository;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.basecamp.backend.domain.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;

    private static final String AUTO_REJECT_REASON = "업체 미응답 자동 반려";

    // 5분마다: 응답 기한 지난 PENDING → REJECTED + 환불
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void expireUnansweredReservations() {
        LocalDateTime now = LocalDateTime.now();

        List<Long> expiredIds = reservationRepository.findExpiredPendingIds(
                ReservationStatus.PENDING, now);
        if (expiredIds.isEmpty()) {
            return;
        }

        int rejected = reservationRepository.bulkReject(
                expiredIds, ReservationStatus.PENDING, ReservationStatus.REJECTED,
                AUTO_REJECT_REASON);

        int refunded = paymentRepository.bulkRefund(
                expiredIds, PaymentStatus.PAID, PaymentStatus.REFUNDED, now);

        log.info("예약 자동 반려 처리: 대상={}건, 반려={}건, 환불={}건", expiredIds.size(), rejected, refunded);
    }
}

