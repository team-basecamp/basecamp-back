package com.basecamp.backend.domain.payment.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.payment.dto.request.PaymentCompleteRequest;
import com.basecamp.backend.domain.payment.dto.request.PaymentPrepareRequest;
import com.basecamp.backend.domain.payment.dto.response.PaymentPrepareResponse;
import com.basecamp.backend.domain.payment.dto.response.PaymentResponse;
import com.basecamp.backend.domain.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  @Operation(
      summary = "결제 준비",
      description =
          """
                    결제창을 띄우기 직전에 호출한다. 결제 대기(PENDING_PAYMENT) 상태의 <b>본인</b> 예약만 가능하다.
                    응답으로 받은 값을 그대로 프론트 SDK의 <code>PortOne.requestPayment()</code>에 넘기면 된다.
                    금액은 서버가 예약 정보로 확정해 내려주므로 프론트에서 계산하거나 바꾸지 않는다.
                    """)
  @PostMapping("/prepare")
  public ResponseEntity<PaymentPrepareResponse> prepare(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody PaymentPrepareRequest request) {
    PaymentPrepareResponse response = paymentService.prepare(request, user.id());

    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "결제 완료 확인",
      description =
          """
                    결제창이 성공으로 닫힌 직후 호출한다. 서버가 포트원에 결제 건을 직접 조회해
                    결제 완료 여부와 금액을 확인한 뒤 예약을 대기(PENDING) 상태로 전환한다.
                    프론트가 보낸 결제 결과 자체는 신뢰하지 않는다.
                    """)
  @PostMapping("/complete")
  public ResponseEntity<PaymentResponse> complete(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody PaymentCompleteRequest request) {
    PaymentResponse response = paymentService.complete(request.paymentId(), user.id());

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
