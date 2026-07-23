package com.basecamp.backend.domain.reservation.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.notification.entity.NotificationType;
import com.basecamp.backend.domain.notification.event.NotificationEvent;
import com.basecamp.backend.domain.payment.service.PaymentService;
import com.basecamp.backend.domain.reservation.dto.request.ReservationCreateRequest;
import com.basecamp.backend.domain.reservation.dto.request.ReservationRejectRequest;
import com.basecamp.backend.domain.reservation.dto.response.MonthlyRevenueResponse;
import com.basecamp.backend.domain.reservation.dto.response.ReservationListResponse;
import com.basecamp.backend.domain.reservation.dto.response.ReservationResponse;
import com.basecamp.backend.domain.reservation.dto.response.ReservationStatsResponse;
import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.basecamp.backend.domain.reservation.repository.MonthlyRevenueProjection;
import com.basecamp.backend.domain.reservation.repository.ReservationRepository;
import com.basecamp.backend.domain.review.repository.ReviewRepository;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

  private final ReservationRepository reservationRepository;
  private final CampRepository campRepository;
  private final PaymentService paymentService;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ReviewRepository reviewRepository;

  @Value("${payment.waiting-expiry-minutes}")
  private long paymentWaitingExpiryMinutes;

  @Transactional
  public ReservationResponse createReservation(ReservationCreateRequest request, Long userId) {
    LocalDateTime paymentValidAfter = LocalDateTime.now().minusMinutes(paymentWaitingExpiryMinutes);

    // 0. 만료된 미결제 이탈 건 정리 → 유니크 키 해제
    reservationRepository.expireStalePaymentWaiting(
        userId,
        request.campId(),
        ReservationStatus.PENDING_PAYMENT,
        ReservationStatus.CANCELLED,
        paymentValidAfter);

    // 1. 활성 예약 기간 겹침 검증 (순차 요청, 기간이 다른 겹침 차단)
    boolean duplicated =
        reservationRepository.existsOverbookingReservation(
            userId,
            request.campId(),
            List.of(ReservationStatus.PENDING, ReservationStatus.RESERVED), // PENDING_PAYMENT 제거
            ReservationStatus.PENDING_PAYMENT, // 별도 파라미터로
            paymentValidAfter,
            request.checkInDate(),
            request.checkOutDate());

    if (duplicated) {
      throw new BusinessException(ErrorCode.DUPLICATE_RESERVATION); // 409
    }

    if (!request.checkOutDate().isAfter(request.checkInDate())) {
      throw new BusinessException(
          ErrorCode.INVALID_RESERVATION_PERIOD, "체크아웃 날짜는 체크인 날짜보다 이후여야 합니다.");
    }

    // 엔티티 조회 (연관관계 매핑용)
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    Camp camp =
        campRepository
            .findById(request.campId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND));

    Reservation reservation =
        Reservation.builder()
            .user(user)
            .camp(camp)
            .checkInDate(request.checkInDate())
            .checkOutDate(request.checkOutDate())
            .guestCount(request.guestCount())
            .totalPrice(request.totalPrice())
            .customerName(request.customerName())
            .customerPhone(request.customerPhone())
            .specialRequest(request.specialRequest())
            .status(ReservationStatus.PENDING_PAYMENT)
            .createdAt(LocalDateTime.now())
            .build();

    Reservation saved;
    try {
      saved = reservationRepository.saveAndFlush(reservation);
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.DUPLICATE_RESERVATION);
    }

    return ReservationResponse.from(saved);
  }

  // 고객이 예약취소
  @Transactional
  public ReservationResponse cancelReservation(Long reservationId, Long userId) {
    Reservation cancelled =
        reservationRepository
            .findById(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    // 예약 취소시 본인 검증
    if (!cancelled.getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    // 환불 조건 확인을 위한 예약 상태 확인
    boolean wasPaid = cancelled.getStatus() != ReservationStatus.PENDING_PAYMENT;

    cancelled.cancel(); // 예약상태변경(CANCELLED, cancel_date값 할당)
    if (wasPaid) {
      paymentService.refund(reservationId); // PENDING/RESERVED = 결제 완료 상태였으므로 환불
    }

    // 커밋 이후 캠핑업체에 취소 알림 (AFTER_COMMIT 리스너가 저장·push). 소유자가 없는 캠핑장은 건너뛴다.
    Long ownerId = cancelled.getCamp().getOwnerId();
    if (ownerId != null && wasPaid) {
      eventPublisher.publishEvent(
          NotificationEvent.of(
              ownerId,
              NotificationType.RESERVATION_CANCELLED,
              cancelled.getId(),
              cancelled.getCamp().getFacltNm()));
    }

    // 취소한 사용자에게도 취소 알림
    eventPublisher.publishEvent(
        NotificationEvent.of(
            userId,
            NotificationType.RESERVATION_CANCELLED,
            cancelled.getId(),
            cancelled.getCamp().getFacltNm()));

    return ReservationResponse.from(cancelled);
  }

  // 캠핑장이 삭제될 때 진행 중인 예약(결제대기/승인대기)을 전부 취소·환불 처리한다.
  @Transactional
  public void cancelAllForDeletedCamp(Long campId) {
    List<ReservationStatus> activeStatuses =
        List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.PENDING);

    List<Reservation> reservations =
        reservationRepository.findAllByCamp_CampIdAndStatusIn(campId, activeStatuses);

    for (Reservation reservation : reservations) {
      ReservationStatus prev = reservation.getStatus();

      reservation.cancel(); // 예약상태변경(CANCELLED, cancel_date값 할당)
      if (prev == ReservationStatus.PENDING) {
        paymentService.refund(reservation.getId()); // PENDING/RESERVED = 결제 완료 상태였으므로 환불
      }

      // 커밋 이후 예약자에게 취소 알림. 캠핑장을 지운 업체 본인에게는 보내지 않는다.
      eventPublisher.publishEvent(
          NotificationEvent.of(
              reservation.getUser().getId(),
              NotificationType.RESERVATION_CANCELLED,
              reservation.getId(),
              reservation.getCamp().getFacltNm()));
    }
  }

  // 업체가 대기중인 예약을 수락
  @Transactional
  public ReservationResponse approveReservation(Long reservationId, Long ownerId) {
    Reservation reservation =
        reservationRepository
            .findById(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    reservation.getCamp().validateOwner(ownerId); // 소유권 검증
    reservation.approve(); // 예약상태변경(RESERVED)

    // 커밋 이후 예약자에게 확정 알림 (AFTER_COMMIT 리스너가 저장·push)
    eventPublisher.publishEvent(
        NotificationEvent.of(
            reservation.getUser().getId(),
            NotificationType.RESERVATION_CONFIRMED,
            reservation.getId(),
            reservation.getCamp().getFacltNm()));

    return ReservationResponse.from(reservation);
  }

  // 업체가 대기중인 예약을 거절(사유 필수)
  @Transactional
  public ReservationResponse rejectReservation(
      Long reservationId, ReservationRejectRequest request, Long ownerId) {
    Reservation reservation =
        reservationRepository
            .findById(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    reservation.getCamp().validateOwner(ownerId); // 소유권 검증
    reservation.reject(request.reason()); // 예약상태변경(REJECTED, reject_reason값 할당)
    paymentService.refund(reservationId); // PENDING = 결제 완료 상태이므로 항상 환불

    // 커밋 이후 예약자에게 거절 알림 (AFTER_COMMIT 리스너가 저장·push)
    eventPublisher.publishEvent(
        NotificationEvent.of(
            reservation.getUser().getId(),
            NotificationType.RESERVATION_REJECTED,
            reservation.getId(),
            reservation.getCamp().getFacltNm()));

    return ReservationResponse.from(reservation);
  }

  // 자동 반려시에 환불
  @Transactional
  public void autoRejectExpired(Long reservationId, String reason) {
    Reservation reservation =
        reservationRepository
            .findById(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    // 대상 목록을 뽑은 뒤 이 건을 처리하기까지 사이에 업체가 수락·거절했을 수 있다.
    // 그 경우 조용히 건너뛴다(이미 사람이 처리한 건을 스케줄러가 덮어쓰면 안 된다).
    if (reservation.getStatus() != ReservationStatus.PENDING) {
      return;
    }

    reservation.reject(reason);
    paymentService.refund(reservationId); // 포트원 취소 API 호출 포함

    // 커밋 이후 예약자에게 거절 알림. 사람이 거절한 경우와 같은 알림 종류를 쓴다(#92 규약과 동일).
    eventPublisher.publishEvent(
        NotificationEvent.of(
            reservation.getUser().getId(),
            NotificationType.RESERVATION_REJECTED,
            reservation.getId(),
            reservation.getCamp().getFacltNm()));
  }

  // 해당 유저 아이디의 예약목록 보여주기
  public Page<ReservationListResponse> findAllReservations(Long userId, Pageable pageable) {
    Page<Reservation> reservations = reservationRepository.findAllByUserId(userId, pageable);

    // 이 페이지에 있는 예약들 중 이미 리뷰가 달린 예약 id만 한 번에 조회해 hasReview를 채운다.
    List<Long> reservationIds = reservations.getContent().stream().map(Reservation::getId).toList();
    Set<Long> reviewedReservationIds =
        reservationIds.isEmpty()
            ? Set.of()
            : Set.copyOf(reviewRepository.findReservationIdsWithReview(reservationIds));

    return reservations.map(
        reservation ->
            ReservationListResponse.from(
                reservation, reviewedReservationIds.contains(reservation.getId())));
  }

  // 해당 캠핑장의 예약목록 보여주기
  public Page<ReservationResponse> findAllReservationsByCamp(
      Long campId, Pageable pageable, Long ownerId) {
    Camp camp =
        campRepository
            .findById(campId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND));

    camp.validateOwner(ownerId); // 소유권 검증

    return reservationRepository
        .findAllByCamp_CampIdAndStatusNot(campId, ReservationStatus.CANCELLED, pageable)
        .map(ReservationResponse::from);
  }

  // 사업자 대시보드용 예약 통계 (RESERVED 확정 예약만 집계, createdAt 기준)
  public ReservationStatsResponse getReservationStats(Long ownerId) {
    List<ReservationStatus> confirmedOnly = List.of(ReservationStatus.RESERVED);

    LocalDate today = LocalDate.now();
    LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
    LocalDateTime nextMonthStart = monthStart.plusMonths(1);
    LocalDateTime yearStart = today.withDayOfYear(1).atStartOfDay();
    LocalDateTime nextYearStart = yearStart.plusYears(1);

    long monthlyRevenue =
        reservationRepository.sumRevenueByOwnerAndPeriod(
            ownerId, confirmedOnly, monthStart, nextMonthStart);
    long monthlyReservations =
        reservationRepository.countByOwnerAndPeriod(
            ownerId, confirmedOnly, monthStart, nextMonthStart);
    long yearlyReservations =
        reservationRepository.countByOwnerAndPeriod(
            ownerId, confirmedOnly, yearStart, nextYearStart);

    // 승인 대기는 "지금 처리해야 할 건"이라 기간으로 자르지 않는다. 지난달에 들어온 미처리 신청도 대기 중이면 세야 한다.
    long pendingCount =
        reservationRepository.countByOwnerAndStatuses(ownerId, List.of(ReservationStatus.PENDING));

    // 평점은 기간 조건 없이 보유 캠핑장 전체를 집계한다. (매출·건수와 달리 이번달/올해로 자르지 않는다)
    return new ReservationStatsResponse(
        monthlyRevenue,
        monthlyReservations,
        yearlyReservations,
        pendingCount,
        roundToFirstDecimal(campRepository.findAverageRatingAcrossOwnedCamps(ownerId)));
  }

  // 평점 표시 단위를 캠핑장 평점(camps.average_rating)과 맞춘다. 리뷰가 없으면(null) 그대로 null을 넘겨
  // "아직 평점 없음"과 "평점이 0.0점"을 화면에서 구분할 수 있게 둔다.
  private Double roundToFirstDecimal(Double value) {
    return value == null
        ? null
        : BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
  }

  // 사업자 대시보드용 예약 통계 (월별 매출, 예약 건수)
  public List<MonthlyRevenueResponse> getMonthlyRevenue(Long ownerId) {
    LocalDate today = LocalDate.now();
    LocalDateTime yearStart = today.withDayOfYear(1).atStartOfDay();
    LocalDateTime nextYearStart = yearStart.plusYears(1);

    Map<Integer, MonthlyRevenueProjection> byMonth =
        reservationRepository
            .findMonthlyRevenueByOwner(
                ownerId, ReservationStatus.RESERVED, yearStart, nextYearStart)
            .stream()
            .collect(Collectors.toMap(MonthlyRevenueProjection::getMonth, p -> p));

    return IntStream.rangeClosed(1, 12)
        .mapToObj(
            m -> {
              MonthlyRevenueProjection p = byMonth.get(m);
              return new MonthlyRevenueResponse(
                  m, p != null ? p.getRevenue() : 0L, p != null ? p.getCount() : 0L);
            })
        .toList();
  }
}
