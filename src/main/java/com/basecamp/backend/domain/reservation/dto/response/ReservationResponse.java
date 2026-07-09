package com.basecamp.backend.domain.reservation.dto.response;

import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL) // 필드에 null이 있으면 response에 포함 안 시키기
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
        LocalDateTime cancelDate,
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
                reservation.getCancelDate(),
                reservation.getTotalPrice(),
                reservation.getCreatedAt()
        );
    }
}
