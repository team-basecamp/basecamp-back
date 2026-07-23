package com.basecamp.backend.security.oauth;

import com.basecamp.backend.security.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OAuth 로그인 CSRF 방지용 state 발급/검증 — 서명된 무상태(stateless) 방식.
 *
 * <p>서버가 {@code nonce.expiry.HMAC(nonce.expiry)} 형태의 서명된 문자열을 발급하고, 콜백에서 서명·만료만 검증한다. 별도 저장소 없이 위조를
 * 막을 수 있어 무상태(JWT) 설계 및 서버 재시작/스케일아웃에 부합한다. 서명 키는 {@code jwt.secret}(HMAC-SHA256)을 재사용한다.
 */
@Component
public class OAuthStateProvider {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final Duration STATE_TTL = Duration.ofMinutes(5);
  private static final char DELIMITER = '.';
  private static final int NONCE_BYTES = 16;

  private final byte[] secret;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();
  private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

  public OAuthStateProvider(JwtProperties jwtProperties, Clock clock) {
    this.secret = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
    this.clock = clock;
  }

  /**
   * 만료시간이 담긴 서명된 state 를 발급한다(형식: {@code nonce.expiry.signature}, base64url 구성이라 '.' 는 구분자로만 등장).
   */
  public String issue() {
    String nonce = randomNonce();
    long expiry = Instant.now(clock).plus(STATE_TTL).toEpochMilli();
    String content = nonce + DELIMITER + expiry;
    return content + DELIMITER + sign(content);
  }

  /** state 의 서명과 만료를 검증한다. 형식 오류·서명 불일치·만료는 모두 {@code false}. */
  public boolean verify(String state) {
    if (!StringUtils.hasText(state)) {
      return false;
    }
    int lastDot = state.lastIndexOf(DELIMITER);
    if (lastDot <= 0) {
      return false;
    }
    String content = state.substring(0, lastDot);
    String signature = state.substring(lastDot + 1);

    int sep = content.indexOf(DELIMITER);
    if (sep <= 0) {
      return false;
    }
    if (!constantTimeEquals(sign(content), signature)) {
      return false;
    }
    return !isExpired(content.substring(sep + 1));
  }

  private boolean isExpired(String expiryMillis) {
    try {
      return Instant.now(clock).toEpochMilli() > Long.parseLong(expiryMillis);
    } catch (NumberFormatException e) {
      return true;
    }
  }

  private String randomNonce() {
    byte[] bytes = new byte[NONCE_BYTES];
    secureRandom.nextBytes(bytes);
    return encoder.encodeToString(bytes);
  }

  private String sign(String content) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      return encoder.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("state 서명에 실패했습니다.", e);
    }
  }

  private boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
