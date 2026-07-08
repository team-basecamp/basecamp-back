package com.basecamp.backend.domain.reservation.dto.response;

import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long campId, //TODO: campId가 아니라 camp엔티티로 바꿔야 하는 점 고려
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int guestCount,
        String customerName,
        String customerPhone,
        String specialRequest,
        ReservationStatus status,
        Long totalPrice,
        LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getCampId(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                reservation.getGuestCount(),
                reservation.getCustomerName(),
                reservation.getCustomerPhone(),
                reservation.getSpecialRequest(),
                reservation.getStatus(),
                reservation.getTotalPrice(),
                reservation.getCreatedAt()
        );
    }
}
