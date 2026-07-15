package com.basecamp.backend.domain.reservation.dto.response;

public record ReservationStatsResponse(
        long monthlyRevenue,        // 이번달 매출 (RESERVED totalPrice 합)
        long monthlyReservations,   // 이번달 예약 건수 (RESERVED)
        long yearlyReservations,    // 올해 누적 예약 건수 (RESERVED)
        Double averageRating        // 평점 — 리뷰 도메인 미구현, null 반환
) {}
