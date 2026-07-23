package com.basecamp.backend.domain.reservation.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record ReservationCreateRequest(
    @NotNull(message = "캠핑장 ID는 필수입니다.") @Positive(message = "캠핑장 ID가 올바르지 않습니다.")
        Long campId, // 이 값은 front에서 받아오기
    @NotBlank(message = "예약자 이름은 필수입니다.") String customerName, // 예약자 이름
    @NotBlank(message = "전화번호는 필수 입력 항목입니다.")
        @Pattern(
            regexp = "^01(?:0|1|[6-9])-\\d{3,4}-\\d{4}$",
            message = "전화번호 형식(010-XXXX-XXXX)이 올바르지 않습니다.")
        String customerPhone, // 예약자 전화번호
    @NotNull(message = "체크인 날짜는 필수입니다.") @Future(message = "체크인 날짜는 내일부터 가능합니다.")
        LocalDate checkInDate, // 체크인 날짜
    @NotNull(message = "체크아웃 날짜는 필수입니다.") @Future(message = "체크아웃 날짜는 내일부터 가능합니다.")
        LocalDate checkOutDate, // 체크아웃 날짜
    @Positive(message = "인원 수는 1명 이상이어야 합니다.") int guestCount, // 예약한 인원 수
    @NotNull(message = "총 결제 금액은 필수입니다.") @Positive(message = "총 결제 금액은 0보다 커야 합니다.")
        Long totalPrice, // 최종적인 결제 금액
    @Size(max = 500, message = "요청사항은 최대 500자까지 입력 가능합니다.") String specialRequest // 고객 요청 사항
    ) {
  // 체크아웃 날짜가 체크인 날짜보다 뒤에 있는지 검증
  @AssertTrue(message = "체크아웃 날짜는 체크인 날짜 이후여야 합니다.")
  public boolean isCheckoutAfterCheckin() {
    if (checkInDate == null || checkOutDate == null) return true;
    return checkOutDate.isAfter(checkInDate);
  }
}
