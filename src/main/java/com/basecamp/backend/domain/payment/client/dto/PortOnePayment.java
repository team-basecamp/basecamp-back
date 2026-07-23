package com.basecamp.backend.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

/**
 * 포트원 결제 단건 조회({@code GET /payments/{paymentId}}) 응답 중 우리가 쓰는 부분만 담는다.
 *
 * <p>포트원 응답에는 여기 선언하지 않은 필드가 많고 버전이 올라가며 더 늘어난다. {@code @JsonIgnoreProperties(ignoreUnknown =
 * true)} 로 무시해야 필드 추가가 우리 서버를 깨뜨리지 않는다 (포트원 문서도 "모르는 필드는 무시하라"고 안내한다).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePayment(
    String id, // 우리가 넘긴 결제 건 ID (= payments.pg_payment_id)
    String status, // READY / PENDING / VIRTUAL_ACCOUNT_ISSUED / PAID / FAILED / CANCELLED /
    // PARTIAL_CANCELLED
    String transactionId, // 포트원이 부여한 결제 시도 ID
    String orderName,
    String currency, // KRW 등
    Amount amount,
    Channel channel,
    Method method,
    Failure failure,
    OffsetDateTime paidAt) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Amount(
      Long total, // 총 결제 금액 — 우리 예약 금액과 반드시 대조해야 하는 값
      Long paid,
      Long cancelled,
      Long taxFree,
      Long vat) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Channel(
      String type, // LIVE / TEST
      String key,
      String name,
      String pgProvider // 실제 결제를 처리한 PG사 (테스트 모드에서도 채널에 설정된 값이 온다)
      ) {}

  /**
   * 결제 수단. {@code type} 이 판별자다: {@code PaymentMethodCard}, {@code PaymentMethodEasyPay}, {@code
   * PaymentMethodTransfer}, {@code PaymentMethodVirtualAccount}, {@code PaymentMethodMobile} 등.
   * 간편결제는 {@code provider}(KAKAOPAY / NAVERPAY / TOSSPAY ...)로 한 번 더 갈린다.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Method(String type, String provider) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Failure(String message, String pgCode, String pgMessage, String reason) {}

  public boolean isPaid() {
    return "PAID".equals(status);
  }

  public boolean isFailed() {
    return "FAILED".equals(status);
  }

  public boolean isCancelled() {
    return "CANCELLED".equals(status) || "PARTIAL_CANCELLED".equals(status);
  }

  public Long totalAmount() {
    return amount != null ? amount.total() : null;
  }

  public String pgProvider() {
    return channel != null ? channel.pgProvider() : null;
  }

  /** 실패 사유를 사람이 읽을 수 있는 한 줄로 만든다. 어느 필드가 채워질지는 PG사마다 달라 순서대로 고른다. */
  public String failureMessage() {
    if (failure == null) {
      return null;
    }
    if (failure.pgMessage() != null && !failure.pgMessage().isBlank()) {
      return failure.pgMessage();
    }
    return failure.message();
  }
}
