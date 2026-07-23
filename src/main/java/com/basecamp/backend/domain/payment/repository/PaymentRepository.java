package com.basecamp.backend.domain.payment.repository;

import com.basecamp.backend.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
  boolean existsByReservationId(Long reservationId);

  Optional<Payment> findByReservationId(Long reservationId);

  /**
   * 결제 건 ID로 결제 행을 <b>잠그고</b> 가져온다.
   *
   * <p>결제 완료는 두 경로로 들어온다: 프론트의 완료 확인 요청과 포트원 웹훅. 둘은 순서 없이, 때로는 거의 동시에 도착한다. 잠그지 않으면 양쪽이 나란히 READY 를
   * 읽고 각자 PAID 로 만들면서 예약 확정 로직이 두 번 돈다. 행 잠금으로 한 쪽이 끝난 뒤에 다른 쪽이 읽게 만들면, 뒤에 온 쪽은 이미 PAID 인 것을 보고 조용히
   * 넘어간다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.pgPaymentId = :pgPaymentId")
  Optional<Payment> findByPgPaymentIdForUpdate(@Param("pgPaymentId") String pgPaymentId);

  Optional<Payment> findByPgPaymentId(String pgPaymentId);

  // 참고: 예전에는 만료 예약을 한 방에 환불 처리하는 bulkRefund 가 여기 있었다.
  // 포트원 연동 후에는 환불이 PG 취소 API 호출을 동반해야 하는데, 일괄 UPDATE 는 그 호출을 건너뛴다.
  // (DB 만 REFUNDED 로 바뀌고 포트원에는 결제가 살아 있는 상태가 된다)
  // 그래서 제거했고, 환불은 PaymentService.refund 한 곳으로만 지나가게 했다.
}
