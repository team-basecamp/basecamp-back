package com.basecamp.backend.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.basecamp.backend.security.config.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OAuthStateProvider} 단위 테스트. 서명 state 의 CSRF 방어 핵심 동작(정상 왕복/위조/만료/키 불일치/형식)을 검증한다. 만료 검증을
 * 위해 고정 {@link Clock} 을 주입한다.
 */
class OAuthStateProviderTest {

  private static final String SECRET = "test-secret-key-for-oauth-state-provider-1234567890";
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant NOW = Instant.parse("2026-07-08T00:00:00Z");

  private OAuthStateProvider providerAt(String secret, Instant instant) {
    return new OAuthStateProvider(
        new JwtProperties(secret, 1800000L, 1209600000L), Clock.fixed(instant, ZONE));
  }

  @Test
  @DisplayName("verify_서버가발급한state_true")
  void verify_발급한state_true() {
    // given
    OAuthStateProvider provider = providerAt(SECRET, NOW);
    String state = provider.issue();

    // when & then
    assertThat(provider.verify(state)).isTrue();
  }

  @Test
  @DisplayName("verify_변조된state_false")
  void verify_변조된state_false() {
    // given
    OAuthStateProvider provider = providerAt(SECRET, NOW);
    String state = provider.issue();
    String tampered = state.substring(0, state.length() - 1) + (state.endsWith("A") ? "B" : "A");

    // when & then
    assertThat(provider.verify(tampered)).isFalse();
  }

  @Test
  @DisplayName("verify_만료된state_false")
  void verify_만료된state_false() {
    // given: NOW 에 발급(만료=NOW+5분), 6분 뒤에 검증
    String state = providerAt(SECRET, NOW).issue();
    OAuthStateProvider later = providerAt(SECRET, NOW.plus(Duration.ofMinutes(6)));

    // when & then
    assertThat(later.verify(state)).isFalse();
  }

  @Test
  @DisplayName("verify_다른secret으로발급한state_false")
  void verify_다른secret_false() {
    // given: 다른 서명 키로 발급된 state
    String foreignState =
        providerAt("another-secret-key-completely-different-0987654321", NOW).issue();

    // when & then
    assertThat(providerAt(SECRET, NOW).verify(foreignState)).isFalse();
  }

  @Test
  @DisplayName("verify_형식오류_false")
  void verify_형식오류_false() {
    // given
    OAuthStateProvider provider = providerAt(SECRET, NOW);

    // when & then
    assertThat(provider.verify("not-a-valid-state")).isFalse();
    assertThat(provider.verify("")).isFalse();
    assertThat(provider.verify(null)).isFalse();
  }
}
