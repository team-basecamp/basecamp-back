package com.basecamp.backend.domain.reservation.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.reservation.dto.request.ReservationCreateRequest;
import com.basecamp.backend.domain.reservation.dto.response.ReservationResponse;
import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.basecamp.backend.domain.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final CampRepository campRepository;

    @Transactional
    public ReservationResponse createReservation(ReservationCreateRequest request) {
        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_PERIOD, "체크아웃 날짜는 체크인 날짜보다 이후여야 합니다.");
        }

        Reservation reservation = Reservation.builder()
                .userId(1L) // TODO: user 엔티티 미구현으로 인한 하드코딩(이건 controller에서 authentication 받기)
                .campId(1L) // TODO: camp 엔티티 미구현으로 인한 하드코딩
                .checkInDate(request.checkInDate())
                .checkOutDate(request.checkOutDate())
                .guestCount(request.guestCount())
                .totalPrice(request.totalPrice())
                .customerName(request.customerName())
                .customerPhone(request.customerPhone())
                .specialRequest(request.specialRequest())
                .status(ReservationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Reservation saved = reservationRepository.save(reservation);
        return ReservationResponse.from(saved);
    }

    // 고객이 예약취소
    @Transactional
    public ReservationResponse cancelReservation(Long reservationId){
        Reservation cancelled = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        cancelled.cancel(); // 예약상태변경(CANCELLED, cancel_date값 할당)

        return ReservationResponse.from(cancelled);
    }

    // 해당 유저 아이디의 예약목록 보여주기
    public Page<ReservationResponse> findAllReservations(Long userId, Pageable pageable){
        return reservationRepository.findAllByUserId(userId, pageable)
                .map(ReservationResponse::from);
    }

    // 해당 캠핑장의 예약목록 보여주기
    public Page<ReservationResponse> findAllReservationsByCamp(Long campId, Pageable pageable){
        if (!campRepository.existsById(campId)) {
            throw new BusinessException(ErrorCode.CAMP_NOT_FOUND);
        }

        return reservationRepository.findAllByCampIdAndStatusNot(campId, ReservationStatus.CANCELLED, pageable)
                .map(ReservationResponse::from);
    }
}
