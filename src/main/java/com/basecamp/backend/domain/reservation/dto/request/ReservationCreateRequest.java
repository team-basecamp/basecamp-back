package com.basecamp.backend.domain.reservation.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record ReservationCreateRequest(
        @NotNull(message = "예약자 이름은 필수입니다.")
        String customerName,

        @NotBlank(message = "전화번호는 필수 입력 항목입니다.")
        @Pattern(
                regexp = "^01(?:0|1|[6-9])-\\d{3,4}-\\d{4}$",
                message = "전화번호 형식(010-XXXX-XXXX)이 올바르지 않습니다."
        )
        String customerPhone,

        @NotNull(message = "체크인 날짜는 필수입니다.")
        @Future(message = "체크인 날짜는 내일부터 가능합니다.")
        LocalDate checkInDate,

        @NotNull(message = "체크아웃 날짜는 필수입니다.")
        @Future(message = "체크아웃 날짜는 내일부터 가능합니다.")
        LocalDate checkOutDate,

        @Positive(message = "인원 수는 1명 이상이어야 합니다.")
        int guestCount,

        @NotNull(message = "총 결제 금액은 필수입니다.")
        @Positive(message = "총 결제 금액은 0보다 커야 합니다.")
        Long totalPrice
) {
        //체크아웃 날짜가 체크인 날짜보다 뒤에 있는지 검증
        @AssertTrue(message = "")
        public boolean isCheckoutAfterCheckin(){
                if(checkInDate == null || checkOutDate == null)
                        return true;
                return checkOutDate.isAfter(checkInDate);
        }
}
