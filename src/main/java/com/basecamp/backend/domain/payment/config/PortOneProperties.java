package com.basecamp.backend.domain.payment.config;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.payment.entity.PaymentMethod;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 포트원(PortOne) V2 연동 설정. {@code application.yml} 의 {@code portone.*} 를 바인딩한다.
 *
 * <p>{@code storeId} / 채널 키는 프론트 결제창 호출에 그대로 노출되는 공개 값이지만, {@code apiSecret} 과 {@code
 * webhookSecret} 은 절대 응답에 실어 보내면 안 된다. 결제 준비 응답({@code PaymentPrepareResponse})이 앞의 것만 담는 이유다.
 *
 * @param storeId 상점 ID (콘솔 → 결제연동 → 식별코드). {@code store-...}
 * @param channelKey 결제 수단별 채널 키. 테스트 모드 채널을 지정하면 실제 승인 없이 결제가 흉내난다.
 * @param apiSecret V2 API Secret. 서버 → 포트원 REST 호출의 Authorization 헤더에 쓴다.
 * @param webhookSecret 웹훅 서명 검증용 시크릿({@code whsec_...}). 비워두면 웹훅 수신을 거부한다.
 * @param apiBaseUrl 포트원 REST API 베이스 URL.
 */
@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
    String storeId,
    ChannelKey channelKey,
    String apiSecret,
    String webhookSecret,
    String apiBaseUrl) {
  public PortOneProperties {
    if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
      apiBaseUrl = "https://api.portone.io";
    }
    if (channelKey == null) {
      channelKey = new ChannelKey(null, null, null);
    }
  }

  /**
   * 결제 수단별 채널 키. 포트원은 PG사·간편결제사마다 채널을 따로 만들게 되어 있어, 어떤 수단으로 결제하느냐에 따라 결제창에 넘길 채널 키가 달라진다.
   *
   * @param inicis KG이니시스 채널 — 카드 결제에 사용
   * @param kakaopay 카카오페이 채널
   * @param tosspay 토스페이 채널
   */
  public record ChannelKey(String inicis, String kakaopay, String tosspay) {}

  /**
   * 결제 수단에 대응하는 채널 키를 고른다.
   *
   * <p>설정되지 않은 수단으로 결제를 시도하면 {@code PG_NOT_CONFIGURED} 로 끊는다. 빈 채널 키를 그대로 프론트에 내려보내면 포트원 결제창이 알아보기
   * 어려운 오류로 죽어 원인 파악이 오래 걸린다.
   */
  public String resolveChannelKey(PaymentMethod method) {
    String key =
        switch (method) {
          case CARD -> channelKey.inicis();
          case KAKAO_PAY -> channelKey.kakaopay();
          case TOSS_PAY -> channelKey.tosspay();
          default -> null;
        };

    if (key == null || key.isBlank()) {
      throw new BusinessException(
          ErrorCode.PG_NOT_CONFIGURED, "%s 결제 채널이 설정되지 않았습니다.".formatted(method));
    }
    return key;
  }

  // 미구현
  public boolean webhookConfigured() {
    return webhookSecret != null && !webhookSecret.isBlank();
  }
}
