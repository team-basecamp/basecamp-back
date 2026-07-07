package com.basecamp.backend.domain.reservation.entity;

public enum ReservationStatus {
    PENDING,    // 예약 신청 (대기)
    RESERVED,   // 예약 확정 (기존 CONFIRMED 에서 변경)
    REJECTED,   // 캠핑업체 거절
    CANCELLED   // 예약 취소
}
