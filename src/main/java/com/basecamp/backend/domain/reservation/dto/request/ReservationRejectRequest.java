package com.basecamp.backend.domain.reservation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReservationRejectRequest(
    @NotBlank(message = "거절 사유는 필수입니다.") @Size(max = 200, message = "거절 사유는 최대 200자까지 입력 가능합니다.")
        String reason) {}
