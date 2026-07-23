package com.basecamp.backend.domain.reservation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "월별 매출 통계 응답")
public record MonthlyRevenueResponse(
    @Schema(description = "월 (1~12)", example = "7") int month, // 1~12
    @Schema(description = "해당 월 매출 합계 (RESERVED 예약 기준, 없으면 0)", example = "1250000")
        long revenue, // 해당 월 매출 (RESERVED만, 없으면 0)
    @Schema(description = "해당 월 예약 건수 (RESERVED 기준, 없으면 0)", example = "8")
        long count // 해당 월 예약 건수 (RESERVED만, 없으면 0)
    ) {}
