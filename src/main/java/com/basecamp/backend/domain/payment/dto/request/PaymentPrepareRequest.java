package com.basecamp.backend.domain.payment.dto.request;

import com.basecamp.backend.domain.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "결제 준비 요청 — 결제창을 띄우기 직전에 호출한다.")
public record PaymentPrepareRequest(
    @Schema(description = "결제할 예약 ID", example = "1")
        @NotNull(message = "예약 ID는 필수입니다.")
        @Positive(message = "예약 ID가 올바르지 않습니다.")
        Long reservationId,
    @Schema(
            description = "사용자가 고른 결제 수단. 포트원 채널이 수단별로 나뉘어 있어 이 값으로 결제창 채널이 결정된다.",
            example = "CARD",
            allowableValues = {"CARD", "KAKAO_PAY", "TOSS_PAY"})
        @NotNull(message = "결제 수단은 필수입니다.")
        PaymentMethod paymentMethod) {}
