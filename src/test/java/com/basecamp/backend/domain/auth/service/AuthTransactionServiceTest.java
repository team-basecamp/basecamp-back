package com.basecamp.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.security.JwtProperties;
import com.basecamp.backend.common.security.JwtTokenProvider;
import com.basecamp.backend.domain.auth.entity.BlacklistReason;
import com.basecamp.backend.domain.auth.entity.TokenBlacklist;
import com.basecamp.backend.domain.auth.repository.TokenBlacklistRepository;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.ImageRepository;
import com.basecamp.backend.domain.user.repository.UserRepository;

/**
 * {@link AuthTransactionService#rotateRefreshToken(Long, String, Instant)} 단위 테스트.
 *
 * <p>회전의 핵심은 "이전 토큰을 확실히 죽이는 것"이므로, 블랙리스트 등록 여부와
 * 폐기된 토큰 재사용 차단을 중심으로 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthTransactionServiceTest {

	private static final String SECRET = "test-secret-key-for-auth-transaction-service-test";
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant NOW = Instant.parse("2026-07-09T00:00:00Z");
	private static final Long USER_ID = 7L;
	private static final String JTI = "0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9";

	@Mock
	private UserRepository userRepository;

	@Mock
	private ImageRepository imageRepository;

	@Mock
	private TokenBlacklistRepository tokenBlacklistRepository;

	private JwtTokenProvider jwtTokenProvider;
	private AuthTransactionService authTransactionService;

	@BeforeEach
	void setUp() {
		jwtTokenProvider = new JwtTokenProvider(new JwtProperties(SECRET, 1_800_000L, 1_209_600_000L));
		authTransactionService = new AuthTransactionService(
				userRepository, imageRepository, tokenBlacklistRepository,
				jwtTokenProvider, Clock.fixed(NOW, ZONE));
	}

	private User activeUser() {
		return User.register("camper", "user@example.com", null, Provider.KAKAO);
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
	@DisplayName("rotateRefreshToken_제재된회원_A004를던진다")
	void rotateRefreshToken_제재된회원_A004를던진다() {
		// given
		User blacklisted = activeUser();
		blacklisted.blacklist();
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(blacklisted));

		// when & then
		assertBusinessException(
				() -> authTransactionService.rotateRefreshToken(USER_ID, JTI, NOW.plusSeconds(3600)),
				ErrorCode.ACCESS_DENIED);
	}

	@Test
	@DisplayName("blacklistToken_이미등록된jti_중복저장하지않는다")
	void blacklistToken_이미등록된jti_중복저장하지않는다() {
		// given: 로그아웃을 두 번 눌러도 결과가 같아야 한다(멱등).
		given(tokenBlacklistRepository.existsByJti(JTI)).willReturn(true);

		// when
		authTransactionService.blacklistToken(
				USER_ID, new RefreshTokenInfo(JTI, NOW.plusSeconds(3600)), BlacklistReason.LOGOUT);

		// then
		verify(tokenBlacklistRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("withdrawUser_정상_탈퇴처리하고_refresh토큰을_WITHDRAWAL사유로_폐기한다")
	void withdrawUser_정상_탈퇴처리하고_refresh토큰을_WITHDRAWAL사유로_폐기한다() {
		// given
		User user = activeUser();
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
		given(tokenBlacklistRepository.existsByJti(JTI)).willReturn(false);

		// when
		authTransactionService.withdrawUser(USER_ID, "사유", new RefreshTokenInfo(JTI, NOW.plusSeconds(3600)));

		// then
		assertThat(user.isWithdrawn()).isTrue();
		assertThat(user.getWithdrawalReason()).isEqualTo("사유");

		ArgumentCaptor<TokenBlacklist> captor = ArgumentCaptor.forClass(TokenBlacklist.class);
		verify(tokenBlacklistRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getReason()).isEqualTo(BlacklistReason.WITHDRAWAL);
	}

	@Test
	@DisplayName("withdrawUser_refresh토큰없음_탈퇴만처리한다")
	void withdrawUser_refresh토큰없음_탈퇴만처리한다() {
		// given
		User user = activeUser();
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

		// when
		authTransactionService.withdrawUser(USER_ID, null, null);

		// then
		assertThat(user.isWithdrawn()).isTrue();
		verify(tokenBlacklistRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("withdrawUser_이미탈퇴한회원_U002를던진다")
	void withdrawUser_이미탈퇴한회원_U002를던진다() {
		// given: access 토큰은 탈퇴 후에도 만료 전까지 유효하므로 중복 탈퇴 요청이 가능하다.
		User withdrawn = activeUser();
		withdrawn.withdraw("사유", Clock.fixed(NOW, ZONE));
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(withdrawn));

		// when & then
		assertBusinessException(
				() -> authTransactionService.withdrawUser(USER_ID, "사유", null),
				ErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("withdrawUser_존재하지않는회원_U002를던진다")
	void withdrawUser_존재하지않는회원_U002를던진다() {
		// given
		given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

		// when & then
		assertBusinessException(
				() -> authTransactionService.withdrawUser(USER_ID, "사유", null),
				ErrorCode.USER_NOT_FOUND);
	}

	private void assertBusinessException(Runnable action, ErrorCode expected) {
		assertThatThrownBy(action::run)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(expected);
	}

}
