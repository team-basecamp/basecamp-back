package com.basecamp.backend.domain.reservation.dto.response;

import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.Reservation.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int guestCount,
        ReservationStatus status,
        Long totalPrice,
        LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                reservation.getGuestCount(),
                reservation.getStatus(),
                reservation.getTotalPrice(),
                reservation.getCreatedAt()
        );
    }
}
