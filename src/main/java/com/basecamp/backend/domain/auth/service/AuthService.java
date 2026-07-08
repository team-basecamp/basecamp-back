package com.basecamp.backend.domain.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.auth.client.OAuthUserInfo;
import com.basecamp.backend.domain.auth.client.SocialClientResolver;
import com.basecamp.backend.domain.user.entity.Provider;

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

	public LoginResult login(Provider provider, String authorizationCode, String state) {
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

}
