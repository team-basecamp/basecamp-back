package com.basecamp.backend.domain.reservation.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.payment.service.PaymentService;
import com.basecamp.backend.domain.reservation.dto.request.ReservationCreateRequest;
import com.basecamp.backend.domain.reservation.dto.request.ReservationRejectRequest;
import com.basecamp.backend.domain.reservation.dto.response.ReservationListResponse;
import com.basecamp.backend.domain.reservation.dto.response.ReservationResponse;
import com.basecamp.backend.domain.reservation.dto.response.ReservationStatsResponse;
import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.basecamp.backend.domain.reservation.repository.ReservationRepository;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final CampRepository campRepository;
    private final PaymentService paymentService;
    private final UserRepository userRepository;

    @Value("${payment.waiting-expiry-minutes}")
    private long paymentWaitingExpiryMinutes;

    @Transactional
    public ReservationResponse createReservation(ReservationCreateRequest request, Long userId) {
        LocalDateTime paymentValidAfter = LocalDateTime.now().minusMinutes(paymentWaitingExpiryMinutes);

        // 0. 만료된 미결제 이탈 건 정리 → 유니크 키 해제
        reservationRepository.expireStalePaymentWaiting(
                userId, request.campId(),
                ReservationStatus.PENDING_PAYMENT, ReservationStatus.CANCELLED,
                paymentValidAfter);

        // 1. 활성 예약 기간 겹침 검증 (순차 요청, 기간이 다른 겹침 차단)
        boolean duplicated = reservationRepository.existsOverbookingReservation(
                userId,
                request.campId(),
                List.of(ReservationStatus.PENDING, ReservationStatus.RESERVED),  // PENDING_PAYMENT 제거
                ReservationStatus.PENDING_PAYMENT,                                // 별도 파라미터로
                paymentValidAfter,
                request.checkInDate(),
                request.checkOutDate());

        if (duplicated) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESERVATION); // 409
        }

        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_PERIOD, "체크아웃 날짜는 체크인 날짜보다 이후여야 합니다.");
        }

        // 엔티티 조회 (연관관계 매핑용)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Camp camp = campRepository.findById(request.campId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND));


        Reservation reservation = Reservation.builder()
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
    public ReservationResponse cancelReservation(Long reservationId, Long userId){
        Reservation cancelled = reservationRepository.findById(reservationId)
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

        return ReservationResponse.from(cancelled);
    }

    // 업체가 대기중인 예약을 수락
    @Transactional
    public ReservationResponse approveReservation(Long reservationId, Long ownerId){
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        reservation.getCamp().validateOwner(ownerId); // 소유권 검증
        reservation.approve(); // 예약상태변경(RESERVED)

        return ReservationResponse.from(reservation);
    }

    // 업체가 대기중인 예약을 거절(사유 필수)
    @Transactional
    public ReservationResponse rejectReservation(Long reservationId, ReservationRejectRequest request, Long ownerId){
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        reservation.getCamp().validateOwner(ownerId); // 소유권 검증
        reservation.reject(request.reason()); // 예약상태변경(REJECTED, reject_reason값 할당)
        paymentService.refund(reservationId); // PENDING = 결제 완료 상태이므로 항상 환불

        return ReservationResponse.from(reservation);
    }

    // 해당 유저 아이디의 예약목록 보여주기
    public Page<ReservationListResponse> findAllReservations(Long userId, Pageable pageable){
        return reservationRepository.findAllByUserId(userId, pageable)
                .map(ReservationListResponse::from);
    }

    // 해당 캠핑장의 예약목록 보여주기
    public Page<ReservationResponse> findAllReservationsByCamp(Long campId, Pageable pageable, Long ownerId){
        Camp camp = campRepository.findById(campId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND));

        camp.validateOwner(ownerId); // 소유권 검증

        return reservationRepository.findAllByCamp_CampIdAndStatusNot(campId, ReservationStatus.CANCELLED, pageable)
                .map(ReservationResponse::from);
    }

    // 사업자 대시보드용 예약 통계 (RESERVED 확정 예약만 집계, createdAt 기준)
    public ReservationStatsResponse getReservationStats(Long ownerId) {
        List<ReservationStatus> confirmedOnly = List.of(ReservationStatus.RESERVED);

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonthStart = monthStart.plusMonths(1);
        LocalDateTime yearStart = LocalDate.now().withDayOfYear(1).atStartOfDay();
        LocalDateTime nextYearStart = yearStart.plusYears(1);

        long monthlyRevenue = reservationRepository.sumRevenueByOwnerAndPeriod(
                ownerId, confirmedOnly, monthStart, nextMonthStart);
        long monthlyReservations = reservationRepository.countByOwnerAndPeriod(
                ownerId, confirmedOnly, monthStart, nextMonthStart);
        long yearlyReservations = reservationRepository.countByOwnerAndPeriod(
                ownerId, confirmedOnly, yearStart, nextYearStart);

        return new ReservationStatsResponse(
                monthlyRevenue,
                monthlyReservations,
                yearlyReservations,
                null // TODO: 리뷰/평점 도메인 구현 후 연동
        );
    }
}
