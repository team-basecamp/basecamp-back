package com.basecamp.backend.domain.payment.dto.response;

import com.basecamp.backend.domain.payment.entity.Payment;
import com.basecamp.backend.domain.payment.entity.PaymentMethod;
import com.basecamp.backend.domain.payment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "결제 내역")
public record PaymentResponse(
    Long id,
    Long reservationId,
    @Schema(description = "포트원 결제 건 ID") String paymentId,
    Long amount,
    @Schema(description = "결제 수단. 결제 완료 전에는 비어 있다.") PaymentMethod paymentMethod,
    @Schema(description = "READY / PAID / REFUNDED / FAILED") PaymentStatus status,
    @Schema(description = "결제 실패 사유. 실패한 경우에만 채워진다.") String failureReason,
    LocalDateTime paidAt,
    LocalDateTime createdAt) {
  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getReservation().getId(),
        payment.getPgPaymentId(),
        payment.getAmount(),
        payment.getPaymentMethod(),
        payment.getStatus(),
        payment.getFailureReason(),
        payment.getPaidAt(),
        payment.getCreatedAt());
  }
}
