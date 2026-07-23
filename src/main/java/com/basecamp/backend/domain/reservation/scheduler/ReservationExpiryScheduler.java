package com.basecamp.backend.domain.reservation.scheduler;

import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.basecamp.backend.domain.reservation.repository.ReservationRepository;
import com.basecamp.backend.domain.reservation.service.ReservationService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 업체가 24시간 안에 응답하지 않은 예약을 자동 반려하고 환불한다.
 *
 * <p><b>일괄 UPDATE 대신 건별로 변경:</b> 환불이 포트원 취소 API 호출을 동반하도록 변경됨. 포트원 API를 추가함으로써 환불시스템 로직에 추가됨으로써 이에
 * 맞게 스케줄러 변경 필요 물리적인 금액과 DB의 금액이 맞지 않아 수정
 *
 * <p>스케줄러 메서드 자체에는 트랜잭션을 걸지 않는다. 건별 트랜잭션은 {@link ReservationService#autoRejectExpired} 가 각자 연다 — 한
 * 건의 실패가 나머지를 되돌리지 않도록.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

  private final ReservationRepository reservationRepository;
  private final ReservationService reservationService;

  private static final int BATCH_SIZE = 100;
  private static final String AUTO_REJECT_REASON = "업체 미응답 자동 반려";

  // 5분마다: 응답 기한 지난 PENDING → REJECTED + 환불
  @Scheduled(fixedDelay = 300_000)
  public void expireUnansweredReservations() {
    List<Long> expiredIds =
        reservationRepository.findExpiredPendingIds(
            ReservationStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
    if (expiredIds.isEmpty()) {
      return;
    }

    int processed = 0;
    int failed = 0;

    for (Long reservationId : expiredIds) {
      try {
        reservationService.autoRejectExpired(reservationId, AUTO_REJECT_REASON);
        processed++;
      } catch (Exception e) {
        // 한 건이 실패해도 나머지는 계속 처리한다. 실패한 건은 PENDING 으로 남아
        // (반려·환불이 같은 트랜잭션이라 함께 롤백된다) 5분 뒤 다시 시도된다.
        failed++;
        log.error("예약 자동 반려·환불 실패 - reservationId: {}, 원인: {}", reservationId, e.getMessage());
      }
    }

    if (failed > 0) {
      // 계속 실패시 PG 설정 문제 or 포트원 API문제. 재시도만 반복되는 현상.
      log.warn(
          "예약 자동 반려 처리: 대상={}건, 완료={}건, 실패={}건 (실패분은 다음 실행에서 재시도)",
          expiredIds.size(),
          processed,
          failed);
      return;
    }

    log.info("예약 자동 반려 처리: 대상={}건, 완료={}건", expiredIds.size(), processed);
  }
}
