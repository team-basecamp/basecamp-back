package com.basecamp.backend.domain.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.security.JwtTokenProvider;
import com.basecamp.backend.common.security.OAuthStateProvider;
import com.basecamp.backend.domain.auth.client.OAuthUserInfo;
import com.basecamp.backend.domain.auth.client.SocialClientResolver;
import com.basecamp.backend.domain.user.entity.Provider;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

/**
 * 소셜 로그인 공통 오케스트레이션.
 *
 * <p>{@code code → (provider별) 토큰교환 + userinfo → email 검증 → email 기준 upsert → 자체 JWT 발급} 흐름을 담당한다.</p>
 *
 * <p><b>트랜잭션 경계:</b> 외부 소셜 제공자 HTTP 호출은 응답 지연 시 DB 커넥션을 오래 점유해 커넥션 풀을 고갈시킬 수
 * 있으므로, 트랜잭션 밖에서 먼저 수행한다. email 검증까지 통과한 뒤의 DB upsert + 토큰 발급만
 * {@link AuthTransactionService} 의 짧은 트랜잭션으로 분리한다.</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

	private final SocialClientResolver socialClientResolver;
	private final AuthTransactionService authTransactionService;
	private final OAuthStateProvider oAuthStateProvider;
	private final JwtTokenProvider jwtTokenProvider;

	/**
	 * 네이버 로그인 시작용 서명 state 를 발급한다. 프론트는 이 state 로 네이버 authorize 를 요청한다.
	 */
	public String issueNaverLoginState() {
		return oAuthStateProvider.issue();
	}

	public LoginResult login(Provider provider, String authorizationCode, String state) {
		// 0) 네이버 CSRF 방지: state 는 서버가 발급한 서명값이어야 한다.
		//    - 빈 값: 잘못된 요청(400). - 서명/만료 검증 실패: 위조·만료된 요청(400).
		//    유효하지 않은 state 를 토큰 교환(502)까지 내려보내지 않고 여기서 막는다.
		if (provider == Provider.NAVER) {
			if (!StringUtils.hasText(state)) {
				throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
			}
			if (!oAuthStateProvider.verify(state)) {
				throw new BusinessException(ErrorCode.INVALID_OAUTH_STATE);
			}
		}

		// 1) 외부 소셜 호출 — 트랜잭션 밖(응답 지연이 DB 커넥션 점유로 이어지지 않도록).
		//    state 는 네이버 토큰 교환에만 쓰이며, 나머지 provider 는 무시한다.
		OAuthUserInfo userInfo = socialClientResolver.resolve(provider).fetchUserInfo(authorizationCode, state);

		// 2) #23 정책: 이메일이 회원 식별의 유일 키다. 미제공(동의 안 함) 시 로그인/가입을 거부한다.
		if (!StringUtils.hasText(userInfo.email())) {
			throw new BusinessException(ErrorCode.EMAIL_CONSENT_REQUIRED);
		}

		// 3) DB upsert + 토큰 발급 — 짧은 트랜잭션.
		try {
			return authTransactionService.upsertUserAndIssueToken(userInfo);
		} catch (DataIntegrityViolationException e) {
			// 동시 최초 가입 경합: 다른 요청이 먼저 같은 email 로 INSERT 를 커밋한 경우 UNIQUE 제약 위반이 난다.
			// 승자 row 는 이미 커밋됐으므로, 한 번 재시도하면 findByEmail 이 이를 찾아 update 경로로 정상 처리된다.
			return authTransactionService.upsertUserAndIssueToken(userInfo);
		}
	}

	/**
	 * refresh 토큰으로 access 토큰을 재발급하고, refresh 토큰도 함께 회전시킨다.
	 *
	 * <p>검증 순서: 존재 → 서명·만료 → {@code type=refresh} → {@code jti} 보유. 통과한 뒤에야 DB 트랜잭션에 진입한다.</p>
	 *
	 * @param refreshToken HttpOnly 쿠키에서 꺼낸 값. 쿠키가 없으면 {@code null}
	 */
	public TokenRefreshResult refresh(String refreshToken) {
		if (!StringUtils.hasText(refreshToken)) {
			throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
		}

		Claims claims;
		try {
			claims = jwtTokenProvider.parseClaims(refreshToken);
		} catch (ExpiredJwtException e) {
			throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
		} catch (JwtException | IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		// access 토큰을 refresh 로 대신 제출하는 것을 막는다(수명이 짧은 토큰을 무한 연장하는 우회 경로).
		if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(jwtTokenProvider.getType(claims))) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		// jti 도입(#39) 이전에 발급된 토큰은 폐기 대상을 특정할 수 없어 회전이 불가능하다. 재로그인을 유도한다.
		String jti = jwtTokenProvider.getJti(claims);
		if (!StringUtils.hasText(jti)) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		try {
			return authTransactionService.rotateRefreshToken(
					jwtTokenProvider.getUserId(claims), jti, jwtTokenProvider.getExpiresAt(claims));
		} catch (DataIntegrityViolationException e) {
			// 같은 refresh 토큰으로 동시에 재발급 요청이 들어와 UNIQUE(jti) 를 위반한 경우.
			// 정상 사용자의 중복 요청일 수도 있으나, 토큰 재사용과 구분할 수 없으므로 보수적으로 거부한다.
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
	}

}
