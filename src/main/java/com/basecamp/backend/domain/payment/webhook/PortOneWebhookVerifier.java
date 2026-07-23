package com.basecamp.backend.domain.payment.webhook;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.payment.config.PortOneProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 포트원 웹훅 서명 검증. 포트원은 <a href="https://www.standardwebhooks.com">Standard Webhooks</a> 규격을 따른다.
 *
 * <p><b>왜 필요한가:</b> 웹훅 엔드포인트는 포트원 서버가 호출해야 하므로 우리 JWT 인증을 걸 수 없어 공개돼 있다. 서명을 검증하지 않으면 누구나 "결제 완료"
 * 웹훅을 흉내내 예약을 공짜로 확정시킬 수 있다. 서명 검증이 이 엔드포인트의 유일한 인증 수단이다.
 *
 * <p>검증 절차 (Standard Webhooks):
 *
 * <ol>
 *   <li>서명 대상 문자열을 {@code {webhook-id}.{webhook-timestamp}.{본문}} 으로 조립한다.
 *   <li>시크릿({@code whsec_} 접두어를 뗀 base64)을 키로 HMAC-SHA256 을 계산해 base64 로 만든다.
 *   <li>{@code webhook-signature} 헤더의 {@code v1,<서명>} 목록 중 하나와 일치하면 통과한다.
 *   <li>타임스탬프가 현재 시각과 5분 이상 벌어지면 거부한다(재전송 공격 차단).
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneWebhookVerifier {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String SECRET_PREFIX = "whsec_";
  private static final String SIGNATURE_VERSION = "v1";

  // 재전송 공격 방지용 허용 오차. Standard Webhooks 권장값과 동일하다.
  private static final long TOLERANCE_SECONDS = 300;

  private final PortOneProperties properties;

  /**
   * @param webhookId {@code webhook-id} 헤더
   * @param webhookTimestamp {@code webhook-timestamp} 헤더 (Unix epoch 초)
   * @param webhookSignature {@code webhook-signature} 헤더 ({@code v1,<base64>} 공백 구분 목록)
   * @param rawBody 요청 본문 <b>원문</b>. 역직렬화 후 다시 직렬화한 문자열을 쓰면 바이트가 달라져 서명이 깨진다.
   * @throws BusinessException 서명이 맞지 않거나 시각이 어긋난 경우
   */
  public void verify(
      String webhookId, String webhookTimestamp, String webhookSignature, String rawBody) {
    if (!properties.webhookConfigured()) {
      // 시크릿이 없으면 검증할 방법이 없다. 통과시키면 위조 웹훅을 그대로 받아들이므로 거부한다.
      log.error("포트원 웹훅 시크릿이 설정되지 않아 웹훅을 거부한다 (portone.webhook-secret)");
      throw new BusinessException(ErrorCode.PG_NOT_CONFIGURED);
    }
    if (isBlank(webhookId) || isBlank(webhookTimestamp) || isBlank(webhookSignature)) {
      throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "웹훅 서명 헤더가 누락되었습니다.");
    }

    verifyTimestamp(webhookTimestamp);

    String expected = sign(webhookId + "." + webhookTimestamp + "." + rawBody);

    for (String part : webhookSignature.split(" ")) {
      // 각 항목은 "v1,<base64 서명>" 형태. 버전이 다른 항목은 건너뛴다.
      int comma = part.indexOf(',');
      if (comma < 0) {
        continue;
      }
      if (!SIGNATURE_VERSION.equals(part.substring(0, comma))) {
        continue;
      }
      String candidate = part.substring(comma + 1);
      // 타이밍 공격을 막기 위해 문자열 equals 대신 상수 시간 비교를 쓴다.
      if (MessageDigest.isEqual(
          candidate.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
        return;
      }
    }

    log.warn("포트원 웹훅 서명 불일치 - webhookId: {}", webhookId);
    throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
  }

  private void verifyTimestamp(String webhookTimestamp) {
    long sent;
    try {
      sent = Long.parseLong(webhookTimestamp.trim());
    } catch (NumberFormatException e) {
      throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "웹훅 타임스탬프 형식이 올바르지 않습니다.");
    }

    long now = Instant.now().getEpochSecond();
    if (Math.abs(now - sent) > TOLERANCE_SECONDS) {
      throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "웹훅 타임스탬프가 허용 범위를 벗어났습니다.");
    }
  }

  private String sign(String signedContent) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secretKeyBytes(), HMAC_ALGORITHM));
      byte[] digest = mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "웹훅 서명 계산에 실패했습니다.");
    }
  }

  // 시크릿은 "whsec_" + base64 형태로 발급된다. 접두어를 떼고 base64 를 디코딩한 바이트가 실제 HMAC 키다.
  private byte[] secretKeyBytes() {
    String secret = properties.webhookSecret();
    String encoded =
        secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
    return Base64.getDecoder().decode(encoded);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
