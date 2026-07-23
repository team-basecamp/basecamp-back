package com.basecamp.backend.domain.payment.entity;

public enum PaymentStatus {
  READY, // 결제창 호출 준비 완료 (아직 결제되지 않음)
  PAID, // 결제 완료
  REFUNDED, // 환불 완료
  FAILED // 결제 실패
}
