package com.basecamp.backend.domain.reservation.repository;

public interface MonthlyRevenueProjection {
  Integer getMonth(); // 월

  Long getRevenue(); // 월 매출

  Long getCount(); // 월 건수
}
