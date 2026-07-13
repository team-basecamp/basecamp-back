package com.basecamp.backend.domain.payment.dto.request;

import com.basecamp.backend.domain.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentCreateRequest(
        @NotNull(message = "예약 ID는 필수입니다.")
        @Positive(message = "예약 ID가 올바르지 않습니다.")
        Long reservationId,

        @NotNull(message = "결제 수단은 필수입니다.")
        PaymentMethod paymentMethod
) {
}
