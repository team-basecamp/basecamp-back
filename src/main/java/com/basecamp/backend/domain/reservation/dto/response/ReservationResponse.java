package com.basecamp.backend.domain.reservation.dto.response;

import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL) // 필드에 null이 있으면 response에 포함 안 시키기
public record ReservationResponse(
    Long id,
    Long campId,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int guestCount,
    String customerName,
    String customerPhone,
    String specialRequest,
    ReservationStatus status,
    LocalDateTime cancelDate,
    Long totalPrice,
    LocalDateTime createdAt) {
  public static ReservationResponse from(Reservation reservation) {
    return new ReservationResponse(
        reservation.getId(),
        // reservation.getCampId(),
        reservation.getCamp().getCampId(),
        reservation.getCheckInDate(),
        reservation.getCheckOutDate(),
        reservation.getGuestCount(),
        reservation.getCustomerName(),
        reservation.getCustomerPhone(),
        reservation.getSpecialRequest(),
        reservation.getStatus(),
        reservation.getCancelAt(),
        reservation.getTotalPrice(),
        reservation.getCreatedAt());
  }
}
