package com.basecamp.backend.domain.payment.client;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.payment.client.dto.PortOnePayment;
import com.basecamp.backend.domain.payment.entity.PaymentMethod;
import lombok.extern.slf4j.Slf4j;

/**
 * 포트원 결제 수단({@code method.type} + 간편결제 {@code provider})을 우리 {@link PaymentMethod} 로 옮긴다.
 *
 * <p>우리가 취급하는 수단은 카드와 간편결제 3종(카카오/네이버/토스)뿐이고, 결제창에도 이것만 노출한다. 따라서 그 밖의 수단은 정상 흐름에서 들어올 수 없다.
 *
 * <p><b>모르는 수단을 만나면 예외 대신 {@code null} 을 돌려주는 이유:</b> 이 매퍼가 호출되는 시점은 이미 결제가 승인되어 돈이 빠져나간 뒤다. 수단 이름을
 * 우리 enum 에 담지 못한다는 이유로 예외를 던지면 결제 확정 트랜잭션이 통째로 롤백되어, <b>돈은 받았는데 예약은 잡히지 않는</b> 최악의 상태가 된다. 수단은
 * 통계·표시용 부가 정보이므로 비워두고 경고만 남긴 뒤 결제 확정을 진행시킨다 ({@code payments.payment_method} 는 NULL 을 허용한다).
 */
@Slf4j
public final class PortOneMethodMapper {

  private PortOneMethodMapper() {}

  public static PaymentMethod from(PortOnePayment.Method method) {
    if (method == null || method.type() == null) {
      log.warn("포트원 응답에 결제 수단 정보가 없다 - 수단 미기록으로 진행");
      return null;
    }

    return switch (method.type()) {
      case "PaymentMethodCard" -> PaymentMethod.CARD;
      case "PaymentMethodEasyPay" -> easyPay(method.provider());
      default -> {
        log.warn("지원하지 않는 결제 수단 - type: {} - 수단 미기록으로 진행", method.type());
        yield null;
      }
    };
  }

  private static PaymentMethod easyPay(String provider) {
    if (provider == null) {
      log.warn("간편결제 제공사 정보가 없다 - 수단 미기록으로 진행");
      return null;
    }

    return switch (provider) {
      case "KAKAOPAY" -> PaymentMethod.KAKAO_PAY;
        // case "NAVERPAY" -> PaymentMethod.NAVER_PAY;
      case "TOSSPAY" -> PaymentMethod.TOSS_PAY;
      default -> throw new BusinessException(ErrorCode.UNSUPPORTED_PAYMENT_METHOD);
    };
  }
}
