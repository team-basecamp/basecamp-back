package com.basecamp.backend.domain.payment.dto.response;

import com.basecamp.backend.domain.payment.entity.Payment;
import com.basecamp.backend.domain.payment.entity.PaymentMethod;
import com.basecamp.backend.domain.payment.entity.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long reservationId,
        Long amount,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getReservation().getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getCreatedAt()
        );
    }
}
