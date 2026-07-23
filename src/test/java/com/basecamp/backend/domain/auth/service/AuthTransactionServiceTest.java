package com.basecamp.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.auth.client.OAuthUserInfo;
import com.basecamp.backend.domain.auth.entity.BlacklistReason;
import com.basecamp.backend.domain.auth.entity.TokenBlacklist;
import com.basecamp.backend.domain.auth.repository.TokenBlacklistRepository;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.ImageRepository;
import com.basecamp.backend.domain.user.repository.UserRepository;
import com.basecamp.backend.security.cache.TokenBlacklistCache;
import com.basecamp.backend.security.config.JwtProperties;
import com.basecamp.backend.security.jwt.JwtTokenProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link AuthTransactionService#rotateRefreshToken(Long, String, Instant)} 단위 테스트.
 *
 * <p>회전의 핵심은 "이전 토큰을 확실히 죽이는 것"이므로, 블랙리스트 등록 여부와 폐기된 토큰 재사용 차단을 중심으로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthTransactionServiceTest {

  private static final String SECRET = "test-secret-key-for-auth-transaction-service-test";
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant NOW = Instant.parse("2026-07-09T00:00:00Z");
  private static final Long USER_ID = 7L;
  private static final String JTI = "0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9";

  @Mock private UserRepository userRepository;

  @Mock private ImageRepository imageRepository;

  @Mock private TokenBlacklistRepository tokenBlacklistRepository;

  @Mock private TokenBlacklistCache tokenBlacklistCache;

  private JwtTokenProvider jwtTokenProvider;
  private AuthTransactionService authTransactionService;

  @BeforeEach
  void setUp() {
    jwtTokenProvider = new JwtTokenProvider(new JwtProperties(SECRET, 1_800_000L, 1_209_600_000L));
    authTransactionService =
        new AuthTransactionService(
            userRepository,
            imageRepository,
            tokenBlacklistRepository,
            tokenBlacklistCache,
            jwtTokenProvider,
            Clock.fixed(NOW, ZONE));
  }

  private static final String EMAIL = "user@example.com";

  private User activeUser() {
    return User.register("camper", EMAIL, null, Provider.KAKAO);
  }

  private User activeUserWithId(long id) {
    User user = activeUser();
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private User blacklistedUser() {
    User user = activeUser();
    user.blacklist("어뷰징", Clock.fixed(NOW, ZONE));
    return user;
  }

  private OAuthUserInfo kakaoUserInfo() {
    return new OAuthUserInfo(Provider.KAKAO, EMAIL, "camper", null);
  }

  @Test
  @DisplayName("upsert_탈퇴회원의이메일로재로그인_기존행을부활시키지않고_새user_id로가입한다")
  void upsert_탈퇴회원의이메일로재로그인_기존행을부활시키지않고_새userId로가입한다() {
    // given: 탈퇴 회원은 활성 회원 조회에 잡히지 않는다(soft delete 된 옛 행은 email 을 그대로 들고 남아 있다).
    given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.empty());
    given(userRepository.save(any(User.class)))
        .willAnswer(
            invocation -> {
              User saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", 2L);
              return saved;
            });

    // when
    LoginResult result = authTransactionService.upsertUserAndIssueToken(kakaoUserInfo());

    // then: 옛 행(user_id=1)을 되살리는 대신 새 행이 만들어진다.
    verify(userRepository).save(any(User.class));
    assertThat(result.response().userId()).isEqualTo(2L);
    assertThat(result.response().email()).isEqualTo(EMAIL);
  }

  @Test
  @DisplayName("upsert_활성회원_동일provider_기존행을갱신하고_가입하지않는다")
  void upsert_활성회원_동일provider_기존행을갱신하고_가입하지않는다() {
    // given
    given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL))
        .willReturn(Optional.of(activeUserWithId(1L)));

    // when
    LoginResult result = authTransactionService.upsertUserAndIssueToken(kakaoUserInfo());

    // then
    assertThat(result.response().userId()).isEqualTo(1L);
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("upsert_활성회원_다른provider_O006을던진다")
  void upsert_활성회원_다른provider_O006을던진다() {
    // given: 카카오로 가입된 활성 회원의 이메일로 네이버 로그인을 시도한다.
    given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL))
        .willReturn(Optional.of(activeUserWithId(1L)));
    OAuthUserInfo naverUserInfo = new OAuthUserInfo(Provider.NAVER, EMAIL, "camper", null);

    // when & then
    assertBusinessException(
        () -> authTransactionService.upsertUserAndIssueToken(naverUserInfo),
        ErrorCode.EMAIL_ALREADY_REGISTERED);
  }

  @Test
  @DisplayName("rotateRefreshToken_정상_이전jti를블랙리스트에등록하고_새토큰을발급한다")
  void rotateRefreshToken_정상_이전jti를블랙리스트에등록하고_새토큰을발급한다() {
    // given
    Instant expiresAt = NOW.plusSeconds(3600);
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser()));
    given(tokenBlacklistRepository.existsByJti(JTI)).willReturn(false);

    // when
    TokenRefreshResult result = authTransactionService.rotateRefreshToken(USER_ID, JTI, expiresAt);

    // then
    ArgumentCaptor<TokenBlacklist> captor = ArgumentCaptor.forClass(TokenBlacklist.class);
    verify(tokenBlacklistRepository).saveAndFlush(captor.capture());
    TokenBlacklist saved = captor.getValue();
    assertThat(saved.getJti()).isEqualTo(JTI);
    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getReason()).isEqualTo(BlacklistReason.REFRESH_ROTATED);
    assertThat(saved.getExpiresAt()).isEqualTo(LocalDateTime.ofInstant(expiresAt, ZONE));

    // 재발급 경로는 Redis 를 쓰지도 읽지도 않는다. 캐시 장애가 재발급을 막아선 안 되고,
    // 폐기된 refresh 토큰이 캐시 미스로 되살아나서도 안 되기 때문이다(재사용 탐지는 MySQL 담당).
    verify(tokenBlacklistCache, never()).blacklist(anyString(), any(), any());

    // 새 refresh 토큰은 폐기된 것과 다른 jti 를 가져야 한다(아니면 발급 즉시 블랙리스트에 걸린다).
    assertThat(result.response().accessToken()).isNotBlank();
    assertThat(result.response().tokenType()).isEqualTo("Bearer");
    assertThat(jwtTokenProvider.getJti(jwtTokenProvider.parseClaims(result.refreshToken())))
        .isNotEqualTo(JTI);
    assertThat(jwtTokenProvider.getType(jwtTokenProvider.parseClaims(result.refreshToken())))
        .isEqualTo(JwtTokenProvider.TOKEN_TYPE_REFRESH);
  }

  @Test
  @DisplayName("rotateRefreshToken_이미폐기된jti_재사용으로보고_A006을던진다")
  void rotateRefreshToken_이미폐기된jti_재사용으로보고_A006을던진다() {
    // given
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser()));
    given(tokenBlacklistRepository.existsByJti(JTI)).willReturn(true);

    // when & then
    assertBusinessException(
        () -> authTransactionService.rotateRefreshToken(USER_ID, JTI, NOW.plusSeconds(3600)),
        ErrorCode.INVALID_REFRESH_TOKEN);
    verify(tokenBlacklistRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("rotateRefreshToken_존재하지않는회원_A006을던진다")
  void rotateRefreshToken_존재하지않는회원_A006을던진다() {
    // given
    given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

    // when & then
    assertBusinessException(
        () -> authTransactionService.rotateRefreshToken(USER_ID, JTI, NOW.plusSeconds(3600)),
        ErrorCode.INVALID_REFRESH_TOKEN);
    verify(tokenBlacklistRepository, never()).existsByJti(anyString());
  }

  @Test
  @DisplayName("rotateRefreshToken_탈퇴한회원_A006을던진다")
  void rotateRefreshToken_탈퇴한회원_A006을던진다() {
    // given: 탈퇴 후에도 살아있는 refresh 토큰으로 재발급이 무한 반복되면 안 된다.
    User withdrawn = activeUser();
    withdrawn.withdraw("사유", Clock.fixed(NOW, ZONE));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(withdrawn));

    // when & then
    assertBusinessException(
        () -> authTransactionService.rotateRefreshToken(USER_ID, JTI, NOW.plusSeconds(3600)),
        ErrorCode.INVALID_REFRESH_TOKEN);
  }

  @Test
  @DisplayName("rotateRefreshToken_제재된회원_A007를던진다")
  void rotateRefreshToken_제재된회원_A007를던진다() {
    // given
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(blacklistedUser()));

    // when & then
    assertBusinessException(
        () -> authTransactionService.rotateRefreshToken(USER_ID, JTI, NOW.plusSeconds(3600)),
        ErrorCode.BLACKLISTED_USER);
  }

  @Test
  @DisplayName("upsert_제재된회원의_소셜재로그인_A007를던진다")
  void upsert_제재된회원의_소셜재로그인_A007를던진다() {
    // given: 재발급만 막고 재로그인을 열어두면 제재가 무력화된다(#18).
    given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL))
        .willReturn(Optional.of(blacklistedUser()));

    // when & then
    assertBusinessException(
        () -> authTransactionService.upsertUserAndIssueToken(kakaoUserInfo()),
        ErrorCode.BLACKLISTED_USER);
  }

  @Test
  @DisplayName("blacklistToken_access토큰_Redis를먼저쓰고_MySQL에도기록한다")
  void blacklistToken_access토큰_Redis를먼저쓰고_MySQL에도기록한다() {
    // given
    Instant expiresAt = NOW.plusSeconds(1800);
    given(tokenBlacklistRepository.existsByJti(JTI)).willReturn(false);

    // when
    authTransactionService.blacklistToken(
        USER_ID, TokenInfo.access(JTI, expiresAt), BlacklistReason.LOGOUT);

    // then: MySQL 은 영속 기록·감사, Redis 는 인증 필터가 매 요청 조회하는 캐시다(#39 §5).
    // Redis 를 먼저 써야 커밋과 캐시 반영 사이에 폐기된 토큰이 통과하는 창이 열리지 않는다.
    InOrder inOrder = inOrder(tokenBlacklistCache, tokenBlacklistRepository);
    inOrder.verify(tokenBlacklistCache).blacklist(JTI, expiresAt, NOW);
    inOrder.verify(tokenBlacklistRepository).saveAndFlush(any(TokenBlacklist.class));
  }

  @Test
  @DisplayName("blacklistToken_refresh토큰_Redis에는쓰지않고_MySQL에만기록한다")
  void blacklistToken_refresh토큰_Redis에는쓰지않고_MySQL에만기록한다() {
    // given: 인증 필터는 access 토큰의 jti 만 조회한다(refresh 로는 인증되지 않는다).
    given(tokenBlacklistRepository.existsByJti(JTI)).willReturn(false);

    // when
    authTransactionService.blacklistToken(
        USER_ID, TokenInfo.refresh(JTI, NOW.plusSeconds(1_209_600)), BlacklistReason.LOGOUT);

    // then: 캐시에 넣어봐야 한 번도 읽히지 않고, 최대 14일짜리 키만 쌓인다. 재사용 탐지는 MySQL 이 한다.
    verify(tokenBlacklistCache, never()).blacklist(anyString(), any(), any());
    verify(tokenBlacklistRepository).saveAndFlush(any(TokenBlacklist.class));
  }

  @Test
  @DisplayName("blacklistToken_이미등록된jti_중복저장하지않는다")
  void blacklistToken_이미등록된jti_중복저장하지않는다() {
    // given: 로그아웃을 두 번 눌러도 결과가 같아야 한다(멱등).
    given(tokenBlacklistRepository.existsByJti(JTI)).willReturn(true);

    // when
    authTransactionService.blacklistToken(
        USER_ID, TokenInfo.access(JTI, NOW.plusSeconds(1800)), BlacklistReason.LOGOUT);

    // then
    verify(tokenBlacklistRepository, never()).saveAndFlush(any());
    verify(tokenBlacklistCache, never()).blacklist(anyString(), any(), any());
  }

  @Test
  @DisplayName("withdrawUser_정상_탈퇴처리하고_토큰들을_WITHDRAWAL사유로_폐기한다")
  void withdrawUser_정상_탈퇴처리하고_토큰들을_WITHDRAWAL사유로_폐기한다() {
    // given: 로그아웃/탈퇴는 access + refresh 를 함께 폐기한다.
    User user = activeUser();
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(tokenBlacklistRepository.existsByJti(anyString())).willReturn(false);
    List<TokenInfo> tokens =
        List.of(
            TokenInfo.access("access-jti", NOW.plusSeconds(1800)),
            TokenInfo.refresh(JTI, NOW.plusSeconds(3600)));

    // when
    authTransactionService.withdrawUser(USER_ID, "사유", tokens);

    // then
    assertThat(user.isWithdrawn()).isTrue();
    assertThat(user.getWithdrawalReason()).isEqualTo("사유");

    ArgumentCaptor<TokenBlacklist> captor = ArgumentCaptor.forClass(TokenBlacklist.class);
    verify(tokenBlacklistRepository, times(2)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(TokenBlacklist::getJti, TokenBlacklist::getReason)
        .containsExactly(
            tuple("access-jti", BlacklistReason.WITHDRAWAL),
            tuple(JTI, BlacklistReason.WITHDRAWAL));
  }

  @Test
  @DisplayName("withdrawUser_폐기할토큰없음_탈퇴만처리한다")
  void withdrawUser_폐기할토큰없음_탈퇴만처리한다() {
    // given
    User user = activeUser();
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

    // when
    authTransactionService.withdrawUser(USER_ID, null, List.of());

    // then
    assertThat(user.isWithdrawn()).isTrue();
    verify(tokenBlacklistRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("withdrawUser_이미탈퇴한회원_U002를던진다")
  void withdrawUser_이미탈퇴한회원_U002를던진다() {
    // given: access 토큰은 탈퇴 직후에도 폐기 전까지 유효하므로 중복 탈퇴 요청이 가능하다.
    User withdrawn = activeUser();
    withdrawn.withdraw("사유", Clock.fixed(NOW, ZONE));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(withdrawn));

    // when & then
    assertBusinessException(
        () -> authTransactionService.withdrawUser(USER_ID, "사유", List.of()),
        ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("withdrawUser_존재하지않는회원_U002를던진다")
  void withdrawUser_존재하지않는회원_U002를던진다() {
    // given
    given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

    // when & then
    assertBusinessException(
        () -> authTransactionService.withdrawUser(USER_ID, "사유", List.of()),
        ErrorCode.USER_NOT_FOUND);
  }

  private void assertBusinessException(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(expected);
  }
}
