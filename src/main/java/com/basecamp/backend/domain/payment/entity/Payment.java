package com.basecamp.backend.domain.payment.entity;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.reservation.entity.Reservation;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 건. 예약 1건당 최대 1행(uq_payments_rsv).
 *
 * <p>포트원(PortOne) V2 연동 기준 상태 흐름:
 *
 * <pre>
 *   READY ──(포트원 조회 결과 PAID)──▶ PAID ──(취소 API 성공)──▶ REFUNDED
 *     └────(포트원 조회 결과 FAILED)──▶ FAILED ──(재시도)──▶ READY
 * </pre>
 *
 * <p>READY 행은 결제창을 띄우기 전에 미리 잡아두는 자리다. 돈이 오간 기록이 아니므로 매출 통계는 반드시 {@code status = 'PAID'} 로 걸러서 집계해야
 * 한다.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "payment_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reservation_id", nullable = false, unique = true)
  private Reservation reservation;

  @Column(name = "amount", nullable = false)
  private Long amount;

  // 우리가 발급해 포트원 결제창에 넘기는 결제 건 ID. 결제 완료 확인/웹훅에서 이 값으로 행을 되찾는다.
  @Column(name = "pg_payment_id", length = 80, unique = true)
  private String pgPaymentId;

  // 포트원이 부여하는 결제 시도 ID(transactionId). 고객 문의/장애 추적 시 포트원 콘솔 검색 키.
  @Column(name = "pg_tx_id", length = 80)
  private String pgTxId;

  @Column(name = "pg_provider", length = 30)
  private String pgProvider;

  // 결제창을 띄우기 전(READY)에는 고객이 어떤 수단을 고를지 알 수 없어 비어 있다.
  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", length = 20)
  private PaymentMethod paymentMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PaymentStatus status;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @Column(name = "refunded_at")
  private LocalDateTime refundedAt;

  @Column(name = "failure_reason", length = 255)
  private String failureReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Builder
  private Payment(
      Reservation reservation,
      Long amount,
      String pgPaymentId,
      String pgTxId,
      String pgProvider,
      PaymentMethod paymentMethod,
      PaymentStatus status,
      LocalDateTime paidAt,
      LocalDateTime refundedAt,
      String failureReason,
      LocalDateTime createdAt) {
    this.reservation = reservation;
    this.amount = amount;
    this.pgPaymentId = pgPaymentId;
    this.pgTxId = pgTxId;
    this.pgProvider = pgProvider;
    this.paymentMethod = paymentMethod;
    this.status = status;
    this.paidAt = paidAt;
    this.refundedAt = refundedAt;
    this.failureReason = failureReason;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
  }

  /**
   * 결제창 호출용 READY 행을 새로 만든다. 아직 결제된 것이 아니다.
   *
   * @param requestedMethod 사용자가 고른 결제 수단. 결제 완료 시 포트원이 알려주는 실제 수단으로 덮인다. 미리 담아두는 이유는 결제가 실패했을 때
   *     "무엇으로 시도했는지"가 남아야 하기 때문이다.
   */
  public static Payment ready(
      Reservation reservation, String pgPaymentId, PaymentMethod requestedMethod) {
    return Payment.builder()
        .reservation(reservation)
        .amount(reservation.getTotalPrice())
        .pgPaymentId(pgPaymentId)
        .paymentMethod(requestedMethod)
        .status(PaymentStatus.READY)
        .createdAt(LocalDateTime.now())
        .build();
  }

  /**
   * 이전 시도가 실패해 남은 행을 새 결제 시도용으로 되돌린다.
   *
   * <p>예약당 결제 행은 1개(유니크 키)라 재시도할 때 행을 새로 만들 수 없다. 결제 건 ID를 새로 발급해 갈아끼우는 이유는, 포트원에서 같은 ID로 두 번 결제를
   * 시도할 수 없기 때문이다.
   */
  public void retryWith(String pgPaymentId, PaymentMethod requestedMethod) {
    if (this.status == PaymentStatus.PAID) {
      throw new BusinessException(ErrorCode.ALREADY_PAID);
    }
    this.pgPaymentId = pgPaymentId;
    this.pgTxId = null;
    this.pgProvider = null;
    this.paymentMethod = requestedMethod; // 재시도 때 다른 수단을 고를 수 있다.
    this.failureReason = null;
    this.status = PaymentStatus.READY;
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * 포트원 조회 결과가 PAID 임을 확인한 뒤 결제 완료로 확정한다.
   *
   * <p>이미 PAID 면 아무 것도 하지 않는다. 완료 확인 API와 웹훅이 같은 결제 건에 대해 순서 없이 도착하기 때문에, 두 경로 모두 이 메서드를 거쳐 멱등하게
   * 처리한다.
   *
   * @return 이번 호출로 READY → PAID 전이가 실제로 일어났으면 true
   */
  public boolean markPaid(
      PaymentMethod method, String pgTxId, String pgProvider, LocalDateTime paidAt) {
    if (this.status == PaymentStatus.PAID) {
      return false;
    }
    if (this.status == PaymentStatus.REFUNDED) {
      throw new BusinessException(ErrorCode.PAYMENT_ALREADY_REFUNDED);
    }
    // 포트원이 알려준 실제 수단으로 덮는다. 매핑되지 않는 수단이면 null 이 오는데,
    // 그때는 결제 준비 때 담아둔 "사용자가 고른 수단"을 지우지 않고 그대로 남긴다.
    if (method != null) {
      this.paymentMethod = method;
    }
    this.pgTxId = pgTxId;
    this.pgProvider = pgProvider;
    this.status = PaymentStatus.PAID;
    this.paidAt = paidAt != null ? paidAt : LocalDateTime.now();
    this.failureReason = null;
    this.updatedAt = LocalDateTime.now();
    return true;
  }

  /** 포트원 조회 결과가 FAILED 일 때 사유와 함께 실패로 기록한다. */
  public void markFailed(String reason) {
    if (this.status == PaymentStatus.PAID || this.status == PaymentStatus.REFUNDED) {
      return; // 이미 결제/환불이 확정된 건은 뒤늦은 실패 통보로 덮지 않는다.
    }
    this.status = PaymentStatus.FAILED;
    this.failureReason = truncate(reason);
    this.updatedAt = LocalDateTime.now();
  }

  public void refund() {
    if (this.status != PaymentStatus.PAID) {
      throw new BusinessException(ErrorCode.PAYMENT_NOT_REFUNDABLE);
    }
    this.status = PaymentStatus.REFUNDED;
    this.refundedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  public boolean isPaid() {
    return this.status == PaymentStatus.PAID;
  }

  // failure_reason 은 VARCHAR(255). PG 응답 메시지가 더 길면 잘라 넣는다(기록 실패로 결제 처리를 막지 않는다).
  private static String truncate(String reason) {
    if (reason == null) {
      return null;
    }
    return reason.length() <= 255 ? reason : reason.substring(0, 255);
  }
}
