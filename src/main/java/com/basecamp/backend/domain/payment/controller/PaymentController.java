package com.basecamp.backend.domain.payment.controller;

import com.basecamp.backend.domain.payment.dto.request.PaymentCreateRequest;
import com.basecamp.backend.domain.payment.dto.response.PaymentResponse;
import com.basecamp.backend.domain.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "결제 생성", description = "결제 대기(PENDING_PAYMENT) 상태인 예약을 결제 완료 처리합니다(Mock, PG 미연동). 예약상태는 대기(PENDING)로 전이됩니다.")
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentCreateRequest request) {
        PaymentResponse response = paymentService.createPayment(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
