package com.basecamp.backend.domain.reservation.dto.response;

public record MonthlyRevenueResponse(
        int month,     // 1~12
        long revenue,  // 해당 월 매출 (RESERVED만, 없으면 0)
        long count     // 해당 월 예약 건수 (RESERVED만, 없으면 0)
) {}