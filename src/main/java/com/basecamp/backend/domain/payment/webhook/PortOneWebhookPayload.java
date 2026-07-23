package com.basecamp.backend.domain.payment.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 포트원 웹훅 본문(버전 {@code 2024-04-25}).
 *
 * <p>웹훅은 "이 결제 건에 변화가 있었다"는 알림일 뿐, 결제 결과 자체가 아니다. 그래서 여기 담긴 값으로 결제를 확정하지 않고, {@code paymentId} 로
 * 포트원에 다시 조회해 실제 상태와 금액을 확인한다({@code PaymentService.syncFromPortOne}).
 *
 * @param type 이벤트 종류. {@code Transaction.Paid}, {@code Transaction.Failed}, {@code
 *     Transaction.Cancelled}, {@code Transaction.VirtualAccountIssued} 등.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneWebhookPayload(String type, String timestamp, Data data) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Data(
      String storeId,
      String paymentId, // 우리가 발급한 결제 건 ID (= payments.pg_payment_id)
      String transactionId,
      String cancellationId) {}

  public String paymentId() {
    return data != null ? data.paymentId() : null;
  }

  /** 결제 상태 변화와 무관한 이벤트(빌링키 발급 등)는 처리할 필요가 없다. */
  public boolean isTransactionEvent() {
    return type != null && type.startsWith("Transaction.");
  }
}
