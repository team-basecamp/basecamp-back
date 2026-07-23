package com.basecamp.backend.domain.payment.controller;

import com.basecamp.backend.domain.payment.service.PaymentService;
import com.basecamp.backend.domain.payment.webhook.PortOneWebhookPayload;
import com.basecamp.backend.domain.payment.webhook.PortOneWebhookVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포트원 웹훅 수신 엔드포인트.
 *
 * <p><b>인증이 없는 대신 서명으로 지킨다.</b> 포트원 서버가 호출하므로 우리 JWT를 요구할 수 없어 {@code SecurityConfig} 에서
 * 공개(permitAll)로 열려 있다. 대신 모든 요청은 {@link PortOneWebhookVerifier} 의 HMAC 서명 검증을 통과해야 하며, 실패하면 401 로
 * 끊는다.
 *
 * <p><b>본문을 문자열로 받는 이유:</b> 서명은 전송된 <b>바이트 그대로</b>에 대해 계산된다. DTO로 역직렬화한 뒤 다시 직렬화하면 공백·필드 순서가 달라져
 * 서명이 반드시 깨진다. 그래서 원문을 받아 검증부터 하고, 그 다음에 파싱한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

  private final PaymentService paymentService;
  private final PortOneWebhookVerifier webhookVerifier;
  private final ObjectMapper objectMapper;

  @Operation(
      summary = "포트원 결제 웹훅 수신",
      description =
          """
                    포트원 서버가 결제 상태 변화를 통보하는 엔드포인트다. 사용자가 직접 호출하는 API가 아니다.
                    포트원 관리자콘솔의 웹훅 URL에 이 주소를 등록해두면, 브라우저가 꺼져 결제 완료 확인 요청이
                    오지 않은 경우에도 결제가 정상 확정된다.
                    """)
  @PostMapping
  public ResponseEntity<Void> receive(
      @RequestHeader(value = "webhook-id", required = false) String webhookId,
      @RequestHeader(value = "webhook-timestamp", required = false) String webhookTimestamp,
      @RequestHeader(value = "webhook-signature", required = false) String webhookSignature,
      @RequestBody String rawBody) {

    // 검증 실패는 예외로 올려 401 을 돌려준다(GlobalExceptionHandler 가 변환).
    // 위조된 웹훅을 조용히 200 으로 삼키면, 공격 시도가 로그에만 남고 포트원 콘솔에는 성공으로 보인다.
    webhookVerifier.verify(webhookId, webhookTimestamp, webhookSignature, rawBody);

    PortOneWebhookPayload payload = parse(rawBody);
    if (payload == null || !payload.isTransactionEvent() || payload.paymentId() == null) {
      // 결제와 무관한 이벤트(빌링키 등)나 파싱 불가 본문. 재시도해도 결과가 같으므로 200 으로 종료한다.
      log.info("처리 대상이 아닌 웹훅 - type: {}", payload != null ? payload.type() : "(파싱 실패)");
      return ResponseEntity.ok().build();
    }

    log.info("포트원 웹훅 수신 - type: {}, pgPaymentId: {}", payload.type(), payload.paymentId());
    paymentService.handleWebhook(payload.paymentId());

    // 200 이 아니면 포트원이 재시도한다. 처리에 실패했다면 예외가 올라가 5xx 가 되고,
    // 그때는 재시도가 실제로 도움이 되므로 여기서 예외를 삼키지 않는다.
    return ResponseEntity.ok().build();
  }

  private PortOneWebhookPayload parse(String rawBody) {
    try {
      return objectMapper.readValue(rawBody, PortOneWebhookPayload.class);
    } catch (Exception e) {
      log.warn("포트원 웹훅 본문 파싱 실패: {}", e.getMessage());
      return null;
    }
  }
}
