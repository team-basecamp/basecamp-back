package com.basecamp.backend.domain.payment.entity;

public enum PaymentStatus {
    PAID,      // 결제 완료
    REFUNDED,  // 환불 완료
    FAILED     // 결제 실패
}
