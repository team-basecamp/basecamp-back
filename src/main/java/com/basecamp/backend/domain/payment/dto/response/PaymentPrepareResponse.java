package com.basecamp.backend.domain.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 결제창 호출에 필요한 값 묶음. 프론트는 이 값을 그대로 {@code PortOne.requestPayment()} 에 넘긴다.
 *
 * <p><b>금액을 서버가 내려주는 이유:</b> 프론트가 계산한 금액을 결제창에 넣으면 사용자가 값을 바꿔 100원짜리 결제로 예약을 잡을 수 있다. 금액은 예약에 저장된
 * 값을 서버가 내려주고, 결제 완료 확인 때 포트원 조회 결과와 한 번 더 대조한다.
 *
 * <p><b>채널 키·결제수단 코드까지 서버가 내려주는 이유:</b> 포트원은 PG사마다 채널이 따로라 수단과 채널이 어긋나면 결제창이 뜨지 않는다. 그 짝을 프론트가 외우게
 * 하면 설정이 바뀔 때마다 양쪽을 같이 고쳐야 하므로, 짝짓기는 서버가 하고 프론트는 받은 값을 전달만 한다.
 */
@Schema(description = "결제창 호출 파라미터")
public record PaymentPrepareResponse(
    @Schema(description = "포트원 상점 ID", example = "store-deaa3bf6-b7a0-41e3-ad6a-d9f6d404c1a5")
        String storeId,
    @Schema(description = "선택한 결제 수단에 대응하는 채널 키(테스트 모드 채널)") String channelKey,
    @Schema(description = "이번 결제 시도의 고유 ID. 완료 확인 때 그대로 돌려보낸다.", example = "bc_12_9f8a1c2b3d4e5f60")
        String paymentId,
    @Schema(description = "결제창에 표시될 주문명", example = "가평 별빛 캠핑장 (2026.08.01~2026.08.03)")
        String orderName,
    @Schema(description = "결제 금액(원). 서버가 예약 정보로 확정한 값이다.", example = "120000") Long totalAmount,
    @Schema(description = "포트원 SDK 통화 코드", example = "CURRENCY_KRW") String currency,
    @Schema(
            description = "포트원 SDK 결제수단 코드",
            example = "CARD",
            allowableValues = {"CARD", "EASY_PAY"})
        String payMethod,
    @Schema(
            description = "간편결제 제공사. payMethod 가 EASY_PAY 일 때만 채워진다.",
            example = "KAKAOPAY",
            nullable = true)
        String easyPayProvider,
    @Schema(description = "예약자 이름", example = "김민수") String customerName,
    @Schema(description = "예약자 연락처", example = "010-1234-5678") String customerPhone,
    @Schema(description = "예약자 이메일", example = "minsu.kim@kakao.com") String customerEmail) {}
