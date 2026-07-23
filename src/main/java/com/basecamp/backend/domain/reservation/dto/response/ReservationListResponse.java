package com.basecamp.backend.domain.reservation.dto.response;

import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL) // 필드에 null이 있으면 response에 포함 안 시키기
public record ReservationListResponse(
    Long id,
    // Camp 정보
    Long campId,
    String campName,
    String campImage,
    // Reservation 정보
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int guestCount,
    Long totalPrice,
    String customerName,
    String customerPhone,
    String specialRequest,
    ReservationStatus status,
    String rejectReason,
    LocalDateTime cancelDate,
    LocalDateTime createdAt,
    boolean hasReview) {

  public static ReservationListResponse from(Reservation reservation, boolean hasReview) {
    return new ReservationListResponse(
        reservation.getId(),
        reservation.getCamp().getCampId(),
        reservation.getCamp().getFacltNm(),
        reservation.getCamp().getFirstImageUrl(),
        reservation.getCheckInDate(),
        reservation.getCheckOutDate(),
        reservation.getGuestCount(),
        reservation.getTotalPrice(),
        reservation.getCustomerName(),
        reservation.getCustomerPhone(),
        reservation.getSpecialRequest(),
        reservation.getStatus(),
        reservation.getRejectReason(),
        reservation.getCancelAt(),
        reservation.getCreatedAt(),
        hasReview);
  }
}
