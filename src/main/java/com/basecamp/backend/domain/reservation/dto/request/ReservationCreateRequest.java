package com.basecamp.backend.domain.reservation.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record ReservationCreateRequest(

        @NotNull(message = "체크인 날짜는 필수입니다.")
        @FutureOrPresent(message = "체크인 날짜는 오늘 이후여야 합니다.")
        LocalDate checkInDate,

        @NotNull(message = "체크아웃 날짜는 필수입니다.")
        @Future(message = "체크아웃 날짜는 미래여야 합니다.")
        LocalDate checkOutDate,

        @Positive(message = "인원 수는 1명 이상이어야 합니다.")
        int guestCount,

        @NotNull(message = "총 결제 금액은 필수입니다.")
        @Positive(message = "총 결제 금액은 0보다 커야 합니다.")
        Long totalPrice
) {
}
