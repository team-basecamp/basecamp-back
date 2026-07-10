package com.basecamp.backend.domain.payment.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.payment.dto.request.PaymentCreateRequest;
import com.basecamp.backend.domain.payment.dto.response.PaymentResponse;
import com.basecamp.backend.domain.payment.entity.Payment;
import com.basecamp.backend.domain.payment.entity.PaymentStatus;
import com.basecamp.backend.domain.payment.repository.PaymentRepository;
import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;

    // PG 연동 전 Mock 결제: 결제 대기(PENDING_PAYMENT) 예약을 즉시 결제 완료 처리한다.
    @Transactional
    public PaymentResponse createPayment(PaymentCreateRequest request) {
        Reservation reservation = reservationRepository.findById(request.reservationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (paymentRepository.existsByReservationId(reservation.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_PAID);
        }

        reservation.confirmPayment(); // 예약상태변경(PENDING_PAYMENT -> PENDING), RESERVATION_NOT_PENDING_PAYMENT 검증 포함

        Payment payment = Payment.builder()
                .reservation(reservation)
                .amount(reservation.getTotalPrice())
                .paymentMethod(request.paymentMethod())
                .status(PaymentStatus.PAID)
                .paidAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        Payment saved = paymentRepository.save(payment);
        return PaymentResponse.from(saved);
    }
}
