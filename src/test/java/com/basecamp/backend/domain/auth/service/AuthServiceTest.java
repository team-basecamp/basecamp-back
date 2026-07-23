package com.basecamp.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.auth.client.SocialClientResolver;
import com.basecamp.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.basecamp.backend.domain.auth.entity.BlacklistReason;
import com.basecamp.backend.security.config.JwtProperties;
import com.basecamp.backend.security.jwt.JwtTokenProvider;
import com.basecamp.backend.security.oauth.OAuthStateProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@link AuthService#refresh(String)} 단위 테스트.
 *
 * <p>토큰 생성/파싱은 실제 {@link JwtTokenProvider} 를 쓰고(서명·클레임 동작이 검증 대상이므로), DB 트랜잭션 구간은 {@link
 * AuthTransactionService} 를 목킹해 경계만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final String SECRET = "test-secret-key-for-auth-service-refresh-unit-test";
  private static final long ACCESS_TTL_MS = 1_800_000L;
  private static final long REFRESH_TTL_MS = 1_209_600_000L;
  private static final Long USER_ID = 7L;
  private static final String ROLE = "CUSTOMER";

  @Mock private SocialClientResolver socialClientResolver;

  @Mock private AuthTransactionService authTransactionService;

  @Mock private OAuthStateProvider oAuthStateProvider;

  private JwtTokenProvider jwtTokenProvider;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    jwtTokenProvider =
        new JwtTokenProvider(new JwtProperties(SECRET, ACCESS_TTL_MS, REFRESH_TTL_MS));
    authService =
        new AuthService(
            socialClientResolver, authTransactionService, oAuthStateProvider, jwtTokenProvider);
  }

  @Test
  @DisplayName("refresh_유효한refresh토큰_회전을위임하고_새토큰을반환한다")
  void refresh_유효한refresh토큰_회전을위임하고_새토큰을반환한다() {
    // given
    String refreshToken = jwtTokenProvider.createRefreshToken(USER_ID, ROLE);
    TokenRefreshResult expected =
        new TokenRefreshResult(TokenRefreshResponse.of("new-access"), "new-refresh");
    given(authTransactionService.rotateRefreshToken(anyLong(), anyString(), any(Instant.class)))
        .willReturn(expected);

    // when
    TokenRefreshResult result = authService.refresh(refreshToken);

    // then
    assertThat(result).isEqualTo(expected);
    // 폐기 대상은 "제출된 바로 그 토큰"의 jti 여야 한다.
    String jti = jwtTokenProvider.getJti(jwtTokenProvider.parseClaims(refreshToken));
    verify(authTransactionService).rotateRefreshToken(eq(USER_ID), eq(jti), any(Instant.class));
  }

  @Test
  @DisplayName("refresh_쿠키없음_A005를던진다")
  void refresh_쿠키없음_A005를던진다() {
    // given: 컨트롤러가 쿠키를 못 찾으면 null 을 넘긴다.

    // when & then
    assertBusinessException(() -> authService.refresh(null), ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    verify(authTransactionService, never()).rotateRefreshToken(anyLong(), anyString(), any());
  }

  @Test
  @DisplayName("refresh_access토큰을제출_A006을던진다")
  void refresh_access토큰을제출_A006을던진다() {
    // given: 서명은 유효하지만 type 클레임이 access 다.
    String accessToken = jwtTokenProvider.createAccessToken(USER_ID, ROLE);

    // when & then
    assertBusinessException(
        () -> authService.refresh(accessToken), ErrorCode.INVALID_REFRESH_TOKEN);
    verify(authTransactionService, never()).rotateRefreshToken(anyLong(), anyString(), any());
  }

  @Test
  @DisplayName("refresh_다른키로서명된토큰_A006을던진다")
  void refresh_다른키로서명된토큰_A006을던진다() {
    // given
    JwtTokenProvider attacker =
        new JwtTokenProvider(
            new JwtProperties(
                "attacker-secret-key-that-is-long-enough-to-sign", ACCESS_TTL_MS, REFRESH_TTL_MS));
    String forged = attacker.createRefreshToken(USER_ID, ROLE);

    // when & then
    assertBusinessException(() -> authService.refresh(forged), ErrorCode.INVALID_REFRESH_TOKEN);
  }

  @Test
  @DisplayName("refresh_형식이깨진토큰_A006을던진다")
  void refresh_형식이깨진토큰_A006을던진다() {
    // given & when & then
    assertBusinessException(
        () -> authService.refresh("not-a-jwt"), ErrorCode.INVALID_REFRESH_TOKEN);
  }

  @Test
  @DisplayName("refresh_만료된refresh토큰_A003을던진다")
  void refresh_만료된refresh토큰_A003을던진다() {
    // given: TTL 을 음수로 주면 발급 즉시 만료된 토큰이 나온다(테스트에서 sleep 을 피하기 위함).
    JwtTokenProvider expiredProvider =
        new JwtTokenProvider(new JwtProperties(SECRET, ACCESS_TTL_MS, -1_000L));
    String expired = expiredProvider.createRefreshToken(USER_ID, ROLE);

    // when & then
    assertBusinessException(() -> authService.refresh(expired), ErrorCode.EXPIRED_TOKEN);
  }

  @Test
  @DisplayName("refresh_동시요청으로jti중복_A006을던진다")
  void refresh_동시요청으로jti중복_A006을던진다() {
    // given: 같은 토큰으로 동시에 재발급이 들어와 UNIQUE(jti) 를 위반한 상황.
    String refreshToken = jwtTokenProvider.createRefreshToken(USER_ID, ROLE);
    given(authTransactionService.rotateRefreshToken(anyLong(), anyString(), any(Instant.class)))
        .willThrow(new DataIntegrityViolationException("duplicate jti"));

    // when & then
    assertBusinessException(
        () -> authService.refresh(refreshToken), ErrorCode.INVALID_REFRESH_TOKEN);
  }

  @SuppressWarnings("unchecked")
  private List<TokenInfo> captureBlacklistedTokens(BlacklistReason reason) {
    ArgumentCaptor<List<TokenInfo>> captor = ArgumentCaptor.forClass(List.class);
    verify(authTransactionService).blacklistTokens(eq(USER_ID), captor.capture(), eq(reason));
    return captor.getValue();
  }

  @Test
  @DisplayName("logout_access와refresh를_모두_LOGOUT사유로_폐기한다")
  void logout_access와refresh를_모두_LOGOUT사유로_폐기한다() {
    // given
    String accessToken = jwtTokenProvider.createAccessToken(USER_ID, ROLE);
    String refreshToken = jwtTokenProvider.createRefreshToken(USER_ID, ROLE);
    String accessJti = jwtTokenProvider.getJti(jwtTokenProvider.parseClaims(accessToken));
    String refreshJti = jwtTokenProvider.getJti(jwtTokenProvider.parseClaims(refreshToken));

    // when
    authService.logout(USER_ID, accessToken, refreshToken);

    // then: refresh 폐기로 재발급이 끊기고, access 폐기로 인증 필터가 즉시 거부한다.
    assertThat(captureBlacklistedTokens(BlacklistReason.LOGOUT))
        .extracting(TokenInfo::jti)
        .containsExactly(accessJti, refreshJti);
  }

  @Test
  @DisplayName("logout_토큰이하나도없음_폐기할것이없어_조용히성공한다")
  void logout_토큰이하나도없음_폐기할것이없어_조용히성공한다() {
    // given & when: 로그아웃은 멱등해야 하므로 예외를 던지지 않는다.
    authService.logout(USER_ID, null, null);

    // then
    verify(authTransactionService, never()).blacklistTokens(anyLong(), any(), any());
  }

  @Test
  @DisplayName("logout_만료된토큰_이미무력하므로_폐기하지않는다")
  void logout_만료된토큰_이미무력하므로_폐기하지않는다() {
    // given
    JwtTokenProvider expiredProvider =
        new JwtTokenProvider(new JwtProperties(SECRET, -1_000L, -1_000L));

    // when
    authService.logout(
        USER_ID,
        expiredProvider.createAccessToken(USER_ID, ROLE),
        expiredProvider.createRefreshToken(USER_ID, ROLE));

    // then
    verify(authTransactionService, never()).blacklistTokens(anyLong(), any(), any());
  }

  @Test
  @DisplayName("logout_다른회원의refresh토큰_타인토큰폐기를막고_내access만폐기한다")
  void logout_다른회원의refresh토큰_타인토큰폐기를막고_내access만폐기한다() {
    // given: 인증된 principal(=USER_ID)의 주인과 쿠키 토큰의 주인이 다르다.
    String myAccessToken = jwtTokenProvider.createAccessToken(USER_ID, ROLE);
    String othersRefreshToken = jwtTokenProvider.createRefreshToken(999L, ROLE);
    String accessJti = jwtTokenProvider.getJti(jwtTokenProvider.parseClaims(myAccessToken));

    // when
    authService.logout(USER_ID, myAccessToken, othersRefreshToken);

    // then
    assertThat(captureBlacklistedTokens(BlacklistReason.LOGOUT))
        .extracting(TokenInfo::jti)
        .containsExactly(accessJti);
  }

  @Test
  @DisplayName("logout_access토큰을_refresh자리에넣어도_폐기하지않는다")
  void logout_access토큰을_refresh자리에넣어도_폐기하지않는다() {
    // given: 종류가 다른 토큰은 폐기 대상이 아니다.
    String accessToken = jwtTokenProvider.createAccessToken(USER_ID, ROLE);

    // when
    authService.logout(USER_ID, null, accessToken);

    // then
    verify(authTransactionService, never()).blacklistTokens(anyLong(), any(), any());
  }

  @Test
  @DisplayName("logout_동시요청으로jti중복_예외없이성공한다")
  void logout_동시요청으로jti중복_예외없이성공한다() {
    // given: 다른 요청이 먼저 같은 jti 를 등록했다. 목표 상태(폐기됨)는 이미 달성됐다.
    String refreshToken = jwtTokenProvider.createRefreshToken(USER_ID, ROLE);
    willThrow(new DataIntegrityViolationException("duplicate jti"))
        .given(authTransactionService)
        .blacklistTokens(anyLong(), any(), any());

    // when & then
    authService.logout(USER_ID, null, refreshToken);
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("withdraw_탈퇴와_access_refresh폐기를_한트랜잭션에위임한다")
  void withdraw_탈퇴와_access_refresh폐기를_한트랜잭션에위임한다() {
    // given
    String accessToken = jwtTokenProvider.createAccessToken(USER_ID, ROLE);
    String refreshToken = jwtTokenProvider.createRefreshToken(USER_ID, ROLE);

    // when
    authService.withdraw(USER_ID, "사유", accessToken, refreshToken);

    // then
    ArgumentCaptor<List<TokenInfo>> captor = ArgumentCaptor.forClass(List.class);
    verify(authTransactionService).withdrawUser(eq(USER_ID), eq("사유"), captor.capture());
    assertThat(captor.getValue()).hasSize(2);
  }

  @Test
  @DisplayName("withdraw_토큰없음_빈리스트로_탈퇴만위임한다")
  void withdraw_토큰없음_빈리스트로_탈퇴만위임한다() {
    // given & when
    authService.withdraw(USER_ID, "사유", null, null);

    // then: 폐기할 토큰이 없어도 탈퇴 자체는 진행되어야 한다.
    verify(authTransactionService).withdrawUser(USER_ID, "사유", List.of());
  }

  private void assertBusinessException(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(expected);
  }
}
