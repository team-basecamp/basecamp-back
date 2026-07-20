package com.basecamp.backend.domain.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "결제 완료 확인 요청 — 프론트에서 결제창이 성공으로 닫힌 직후 호출한다.")
public record PaymentCompleteRequest(
    @Schema(description = "결제 준비 때 발급받은 결제 건 ID", example = "bc_12_9f8a1c2b3d4e5f60")
        @NotBlank(message = "결제 ID는 필수입니다.")
        @Size(max = 80, message = "결제 ID 형식이 올바르지 않습니다.")
        String paymentId) {}
