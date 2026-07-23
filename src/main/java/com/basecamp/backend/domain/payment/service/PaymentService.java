package com.basecamp.backend.domain.payment.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.notification.entity.NotificationType;
import com.basecamp.backend.domain.notification.event.NotificationEvent;
import com.basecamp.backend.domain.payment.client.PortOneClient;
import com.basecamp.backend.domain.payment.client.PortOneMethodMapper;
import com.basecamp.backend.domain.payment.client.dto.PortOnePayment;
import com.basecamp.backend.domain.payment.config.PortOneProperties;
import com.basecamp.backend.domain.payment.dto.request.PaymentPrepareRequest;
import com.basecamp.backend.domain.payment.dto.response.PaymentPrepareResponse;
import com.basecamp.backend.domain.payment.dto.response.PaymentResponse;
import com.basecamp.backend.domain.payment.entity.Payment;
import com.basecamp.backend.domain.payment.entity.PaymentMethod;
import com.basecamp.backend.domain.payment.repository.PaymentRepository;
import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.basecamp.backend.domain.reservation.repository.ReservationRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포트원(PortOne) V2 결제. 테스트 모드 채널 기준이지만, 호출 흐름은 실거래와 동일하다.
 *
 * <p><b>전체 흐름</b>
 *
 * <pre>
 *   1) prepare   : 예약 검증 → READY 결제 행 생성 → 결제창 파라미터 반환
 *   2) (프론트)   : PortOne.requestPayment() 로 결제창 호출
 *   3) complete  : 프론트가 알려준 결제 건 ID로 서버가 포트원에 직접 조회 → 금액 대조 → 예약 확정
 *   3') webhook  : 포트원이 서버로 직접 통보 → 같은 검증 로직 수행 (3과 순서 무관, 멱등)
 * </pre>
 *
 * <p><b>왜 3과 3'이 둘 다 필요한가:</b> 결제창을 닫자마자 브라우저가 꺼지거나 네트워크가 끊기면 3번 호출이 영영 오지 않는다. 그래도 돈은 빠져나갔다. 웹훅은 그
 * 구멍을 메우는 경로이고, 반대로 웹훅이 지연될 때 사용자를 기다리게 하지 않으려면 3번이 필요하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

  // 프론트 SDK(PortOne.requestPayment)의 currency 파라미터에 그대로 넣는 값이다.
  private static final String CURRENCY_KRW = "CURRENCY_KRW";

  private static final DateTimeFormatter ORDER_NAME_DATE =
      DateTimeFormatter.ofPattern("yyyy.MM.dd");

  private final PaymentRepository paymentRepository;
  private final ReservationRepository reservationRepository;
  private final PortOneClient portOneClient;
  private final PortOneProperties portOneProperties;
  private final ApplicationEventPublisher eventPublisher;

  // ---------------------------------------------------------------------
  // 1) 결제 준비
  // ---------------------------------------------------------------------

  /**
   * 결제창을 띄우기 전에 결제 건을 서버에 미리 등록하고, 결제창 호출 파라미터를 돌려준다.
   *
   * <p>금액·주문명을 프론트가 아닌 서버가 정하는 것이 이 단계의 핵심이다. 프론트가 보낸 금액으로 결제창을 열면 사용자가 그 값을 바꿔 헐값 결제를 만들 수 있다.
   */
  @Transactional
  public PaymentPrepareResponse prepare(PaymentPrepareRequest request, Long userId) {
    // 결제 준비와 예약 만료 정리가 겹치지 않도록 예약 행을 잠근다.
    Reservation reservation =
        reservationRepository
            .findByIdForUpdate(request.reservationId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    if (!reservation.getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
      throw new BusinessException(ErrorCode.RESERVATION_NOT_PENDING_PAYMENT);
    }

    PaymentMethod method = request.paymentMethod();

    // 채널 키를 먼저 확인한다. 설정이 빠진 수단이면 결제 행을 만들기 전에 끊어야
    // 쓸모없는 READY 행이 남지 않는다.
    String channelKey = portOneProperties.resolveChannelKey(method);

    String pgPaymentId = generatePgPaymentId(reservation.getId());

    // 예약당 결제 행은 1개(uq_payments_rsv)다. 이전 시도가 실패해 남은 행이 있으면
    // 새로 만드는 대신 새 결제 건 ID로 갈아끼워 재사용한다.
    Payment payment =
        paymentRepository
            .findByReservationId(reservation.getId())
            .map(
                existing -> {
                  if (existing.isPaid()) {
                    throw new BusinessException(ErrorCode.ALREADY_PAID);
                  }
                  existing.retryWith(pgPaymentId, method);
                  return existing;
                })
            .orElseGet(
                () -> paymentRepository.save(Payment.ready(reservation, pgPaymentId, method)));

    log.info(
        "결제 준비 완료 - reservationId: {}, pgPaymentId: {}, method: {}, amount: {}",
        reservation.getId(),
        payment.getPgPaymentId(),
        method,
        payment.getAmount());

    return new PaymentPrepareResponse(
        portOneProperties.storeId(),
        channelKey,
        payment.getPgPaymentId(),
        buildOrderName(reservation),
        payment.getAmount(),
        CURRENCY_KRW,
        payMethodOf(method),
        easyPayProviderOf(method),
        reservation.getCustomerName(),
        reservation.getCustomerPhone(),
        reservation.getUser().getEmail());
  }

  // 포트원 SDK 의 payMethod 코드. 간편결제는 수단이 무엇이든 EASY_PAY 하나로 묶이고,
  // 어느 간편결제사인지는 채널 키와 easyPayProvider 가 결정한다.
  private String payMethodOf(PaymentMethod method) {
    return method == PaymentMethod.CARD ? "CARD" : "EASY_PAY";
  }

  private String easyPayProviderOf(PaymentMethod method) {
    return switch (method) {
      case KAKAO_PAY -> "KAKAOPAY";
      case TOSS_PAY -> "TOSSPAY";
      default -> null;
    };
  }

  // ---------------------------------------------------------------------
  // 2) 결제 완료 확인 (프론트 호출)
  // ---------------------------------------------------------------------

  /**
   * 프론트가 "결제됐다"고 알려온 건을 서버가 포트원에 직접 조회해 확정한다.
   *
   * <p>프론트가 넘긴 결제 결과는 신뢰하지 않는다. 사용자는 브라우저에서 오는 요청을 얼마든지 위조할 수 있으므로, 결제 여부와 금액의 판단 근거는 오직 포트원 조회
   * 응답이다.
   */
  @Transactional
  public PaymentResponse complete(String pgPaymentId, Long userId) {
    Payment payment =
        paymentRepository
            .findByPgPaymentIdForUpdate(pgPaymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

    if (!payment.getReservation().getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    // 확정된 것은 멱등처리
    if (payment.isPaid()) {
      return PaymentResponse.from(payment);
    }

    syncFromPortOne(payment);

    return PaymentResponse.from(payment);
  }

  // ---------------------------------------------------------------------
  // 3) 웹훅 수신 (포트원 호출)
  // ---------------------------------------------------------------------

  /**
   * 웹훅으로 통보받은 결제 건을 동기화한다. 서명 검증은 컨트롤러에서 이미 끝난 상태로 들어온다.
   *
   * <p>우리가 모르는 결제 건(다른 상점/테스트 호출)은 예외 없이 넘긴다. 웹훅에 4xx/5xx 를 돌려주면 포트원이 계속 재시도하는데, 영영 처리할 수 없는 건에
   * 대해서는 재시도가 의미 없기 때문이다.
   */
  @Transactional
  public void handleWebhook(String pgPaymentId) {
    Payment payment = paymentRepository.findByPgPaymentIdForUpdate(pgPaymentId).orElse(null);
    if (payment == null) {
      log.info("우리가 관리하지 않는 결제 건의 웹훅 - pgPaymentId: {} (무시)", pgPaymentId);
      return;
    }

    syncFromPortOne(payment);
  }

  // ---------------------------------------------------------------------
  // 공통: 포트원 조회 결과로 우리 상태를 맞춘다
  // ---------------------------------------------------------------------

  /**
   * 포트원의 결제 상태를 우리 DB에 반영한다. 완료 확인과 웹훅이 공유하는 단 하나의 진입점이다.
   *
   * <p>두 경로 모두 이 메서드를 거치므로 검증 규칙(금액 대조·중복 처리 방지)이 한 곳에만 존재한다. 호출 순서에 관계없이 결과가 같도록(멱등) 설계돼 있어, 웹훅이
   * 먼저 와도 뒤에 온 완료 확인이 예약을 두 번 확정하지 않는다.
   */
  private void syncFromPortOne(Payment payment) {
    PortOnePayment pgPayment = portOneClient.getPayment(payment.getPgPaymentId());

    if (pgPayment.isFailed()) {
      payment.markFailed(pgPayment.failureMessage());
      log.warn(
          "결제 실패 확인 - pgPaymentId: {}, 사유: {}",
          payment.getPgPaymentId(),
          pgPayment.failureMessage());
      return;
    }

    if (!pgPayment.isPaid()) {
      // READY / PENDING / VIRTUAL_ACCOUNT_ISSUED — 아직 결과가 나오지 않았다. 상태를 바꾸지 않고 기다린다.
      log.info(
          "결제 미완료 상태 - pgPaymentId: {}, status: {}", payment.getPgPaymentId(), pgPayment.status());
      return;
    }

    // 결제창에 넣은 금액과 실제 승인 금액이 다르면 위·변조다. 예약을 확정하지 않고, 받은 돈은 돌려준다.
    Long paidAmount = pgPayment.totalAmount();
    if (paidAmount == null || !paidAmount.equals(payment.getAmount())) {
      log.error(
          "결제 금액 불일치 - pgPaymentId: {}, 기대: {}, 실제: {}",
          payment.getPgPaymentId(),
          payment.getAmount(),
          paidAmount);
      payment.markFailed(
          ErrorCode.PAYMENT_AMOUNT_MISMATCH.getMessage()
              + " (기대 %d원 / 실제 %s원)".formatted(payment.getAmount(), paidAmount));
      cancelQuietly(payment.getPgPaymentId(), "결제 금액 불일치로 인한 자동 취소");
      // 여기서 예외를 던지면 트랜잭션이 롤백되면서 방금 남긴 FAILED 기록까지 함께 사라진다.
      // 실패 기록을 커밋하고, 호출한 쪽(complete)이 failureReason 을 담아 응답하게 둔다.
      return;
    }

    boolean firstTransition =
        payment.markPaid(
            PortOneMethodMapper.from(pgPayment.method()),
            pgPayment.transactionId(),
            pgPayment.pgProvider(),
            toLocalDateTime(pgPayment.paidAt()));

    if (!firstTransition) {
      // 다른 경로(웹훅/완료 확인)가 이미 처리했다. 예약을 다시 건드리지 않는다.
      return;
    }

    confirmOrRefund(payment);
  }

  /**
   * 결제가 확정된 뒤 예약을 확정한다. 단, 결제가 진행되는 사이 예약이 이미 취소·만료됐다면 환불한다.
   *
   * <p>미결제 예약은 일정 시간이 지나면 자동으로 정리된다({@code PAYMENT_WAITING_EXPIRY}). 사용자가 결제창을 오래 열어둔 채 결제하면 "결제는
   * 성공했는데 예약은 사라진" 상태가 될 수 있다. 그대로 두면 돈만 받고 방을 안 주는 셈이므로 즉시 되돌린다.
   */
  private void confirmOrRefund(Payment payment) {
    Reservation reservation = payment.getReservation();

    if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
      reservation.confirmPayment(); // PENDING_PAYMENT -> PENDING (업체 승인 대기, 24시간 만료 설정)
      log.info(
          "결제 확정 및 예약 전환 - reservationId: {}, pgPaymentId: {}",
          reservation.getId(),
          payment.getPgPaymentId());

      // 커밋 이후 캠핑업체에 승인 대기 알림. 소유자가 없는 캠핑장은 건너뛴다.
      Long ownerId = reservation.getCamp().getOwnerId();
      if (ownerId != null) {
        eventPublisher.publishEvent(
            NotificationEvent.of(
                ownerId,
                NotificationType.RESERVATION_REQUESTED,
                reservation.getId(),
                reservation.getCamp().getFacltNm()));
      }

      // 결제가 확정되어 PENDING(승인 대기)으로 넘어간 시점에만 예약자에게 알린다.
      // PENDING_PAYMENT 상태(결제 전)에는 "승인을 기다리는 중"이 아직 사실이 아니므로 보내지 않는다.
      eventPublisher.publishEvent(
          NotificationEvent.of(
              reservation.getUser().getId(),
              NotificationType.RESERVATION_APPROVE_WAIT,
              reservation.getId(),
              reservation.getCamp().getFacltNm()));
      return;
    }

    log.warn(
        "결제 성공했으나 예약이 이미 {} 상태 - reservationId: {}, 자동 환불 진행",
        reservation.getStatus(),
        reservation.getId());
    portOneClient.cancelPayment(payment.getPgPaymentId(), "예약이 유효하지 않아 자동 환불");
    payment.refund();
  }

  // ---------------------------------------------------------------------
  // 환불
  // ---------------------------------------------------------------------

  /**
   * 예약 취소·거절에 따른 전액 환불. {@code ReservationService} 가 상태를 바꾼 직후 호출한다.
   *
   * <p>포트원 취소 API를 먼저 호출하고 그 성공을 확인한 뒤에 우리 상태를 바꾼다. 순서를 뒤집으면 PG 취소가 실패했는데 우리 DB만 "환불 완료"로 남아, 돌려주지
   * 않은 돈을 돌려준 것으로 기록하게 된다.
   *
   * <p>포트원 연동 이전에 쌓인 결제 행({@code pg_payment_id} 가 없는 더미/Mock 데이터)은 PG 호출을 건너뛰고 상태만 바꾼다.
   */
  @Transactional
  public void refund(Long reservationId) {
    Payment payment =
        paymentRepository
            .findByReservationId(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

    if (!payment.isPaid()) {
      throw new BusinessException(ErrorCode.PAYMENT_NOT_REFUNDABLE);
    }

    if (payment.getPgPaymentId() != null) {
      portOneClient.cancelPayment(payment.getPgPaymentId(), "예약 취소에 따른 환불");
    } else {
      log.info("PG 결제 건 ID가 없는 결제 - reservationId: {}, PG 취소 없이 상태만 환불 처리", reservationId);
    }

    payment.refund();
  }

  // ---------------------------------------------------------------------
  // 내부 헬퍼
  // ---------------------------------------------------------------------

  /**
   * 결제 건 ID를 만든다. 포트원에서 전역 유일해야 하고, 같은 ID로는 재결제할 수 없다.
   *
   * <p>예약 ID를 앞에 두는 이유는 포트원 콘솔에서 결제 건을 눈으로 찾기 쉽게 하기 위해서다. 뒤의 무작위 값이 재시도마다 새 ID를 보장한다.
   */
  private String generatePgPaymentId(Long reservationId) {
    String random = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    return "bc_" + reservationId + "_" + random;
  }

  private String buildOrderName(Reservation reservation) {
    return "%s (%s~%s)"
        .formatted(
            reservation.getCamp().getFacltNm(),
            reservation.getCheckInDate().format(ORDER_NAME_DATE),
            reservation.getCheckOutDate().format(ORDER_NAME_DATE));
  }

  // 포트원은 RFC 3339(오프셋 포함) 시각을 준다. DB 컬럼은 LocalDateTime 이므로 서버 시간대 기준으로 옮긴다.
  private LocalDateTime toLocalDateTime(OffsetDateTime offsetDateTime) {
    if (offsetDateTime == null) {
      return null;
    }
    return offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
  }

  // 금액 불일치처럼 이미 실패로 결론난 흐름에서의 보상 취소. 여기서 또 예외가 나면 원래 원인이 가려진다.
  private void cancelQuietly(String pgPaymentId, String reason) {
    try {
      portOneClient.cancelPayment(pgPaymentId, reason);
    } catch (RuntimeException e) {
      log.error("자동 취소 실패 - pgPaymentId: {} — 관리자 수동 취소 필요. 원인: {}", pgPaymentId, e.getMessage());
    }
  }
}
