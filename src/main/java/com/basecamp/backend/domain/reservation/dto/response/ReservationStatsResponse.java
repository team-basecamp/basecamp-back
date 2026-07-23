package com.basecamp.backend.domain.reservation.dto.response;

public record ReservationStatsResponse(
    long monthlyRevenue, // 이번달 매출 (RESERVED totalPrice 합)
    long monthlyReservations, // 이번달 예약 건수 (RESERVED)
    long yearlyReservations, // 올해 누적 예약 건수 (RESERVED)
    long pendingCount, // 승인 대기 중인 예약 건수 (PENDING). 지금 처리해야 할 일이라 기간으로 자르지 않는다.
    Double averageRating // 보유 캠핑장 전체 리뷰의 평균 평점 (소수 첫째 자리). 리뷰가 없으면 null
    ) {}
