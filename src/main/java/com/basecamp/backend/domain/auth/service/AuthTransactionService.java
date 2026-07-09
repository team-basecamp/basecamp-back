package com.basecamp.backend.domain.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.security.JwtTokenProvider;
import com.basecamp.backend.domain.auth.client.OAuthUserInfo;
import com.basecamp.backend.domain.auth.dto.response.LoginResponse;
import com.basecamp.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.basecamp.backend.domain.auth.entity.BlacklistReason;
import com.basecamp.backend.domain.auth.entity.TokenBlacklist;
import com.basecamp.backend.domain.auth.repository.TokenBlacklistRepository;
import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.entity.UserStatus;
import com.basecamp.backend.domain.user.repository.ImageRepository;
import com.basecamp.backend.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 소셜 로그인의 DB 트랜잭션 구간.
 *
 * <p>외부 소셜 호출을 제외한 {@code email 기준 upsert + 자체 JWT 발급 + 응답 조립}만 짧은 트랜잭션으로 수행한다.
 * 외부 HTTP 지연이 DB 커넥션 점유로 이어지지 않도록, 외부 호출은 {@link AuthService} 가 트랜잭션 밖에서 먼저
 * 수행하고 그 결과({@link OAuthUserInfo})만 이 트랜잭션으로 넘긴다.</p>
 *
 * <p>{@code @Transactional} 자기 호출(self-invocation)은 프록시를 우회해 트랜잭션이 열리지 않으므로,
 * 트랜잭션 경계를 실제로 분리하려면 이렇게 별도 빈으로 두어야 한다.</p>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthTransactionService {

	private final UserRepository userRepository;
	private final ImageRepository imageRepository;
	private final TokenBlacklistRepository tokenBlacklistRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final Clock clock;

	/**
	 * email 기준으로 회원을 upsert 하고 자체 JWT(access/refresh)를 발급한다.
	 *
	 * <p>동시 최초 가입 경합으로 {@code email} UNIQUE 제약을 위반하면
	 * {@link org.springframework.dao.DataIntegrityViolationException} 이 전파되며,
	 * 재시도 여부는 호출자({@link AuthService})가 결정한다.</p>
	 */
	public LoginResult upsertUserAndIssueToken(OAuthUserInfo userInfo) {
		User user = userRepository.findByEmail(userInfo.email())
				.map(existing -> updateExisting(existing, userInfo))
				.orElseGet(() -> register(userInfo));

		String role = user.getRole().name();
		String accessToken = jwtTokenProvider.createAccessToken(user.getId(), role);
		String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), role);

		// LAZY 연관(profileImage) 접근이 필요하므로 응답 body 는 트랜잭션 내부에서 조립한다.
		return new LoginResult(LoginResponse.from(user, accessToken), refreshToken);
	}

	/**
	 * refresh 토큰을 회전(rotation)한다. 이전 토큰을 블랙리스트에 올려 폐기하고 access/refresh 를 새로 발급한다.
	 *
	 * <p>무상태 JWT 는 서명만 맞으면 만료 전까지 유효하므로, 새 토큰을 내려주는 것만으로는 이전 토큰이 죽지 않는다.
	 * 이전 {@code jti} 를 블랙리스트에 등록해야 비로소 "탈취 토큰의 유효 기간 = 다음 정상 재발급까지"가 성립한다(#39).</p>
	 *
	 * <p>이미 폐기된 jti 로 재발급을 요청하면 탈취 의심으로 보고 거부한다(refresh token reuse detection).
	 * 동시 요청으로 {@code existsByJti} 검사를 둘 다 통과하더라도 {@code idx_bl_jti}(UNIQUE) 가 최종 방어선이며,
	 * 이때 발생하는 {@link org.springframework.dao.DataIntegrityViolationException} 은 호출자가 401 로 변환한다.</p>
	 *
	 * <p>role 은 토큰 클레임을 믿지 않고 DB 에서 다시 읽는다. 권한 변경·탈퇴·제재가 재발급 시점에 반영되어야 한다.</p>
	 *
	 * @param refreshExpiresAt 폐기할 토큰의 만료 시각. 이 시각이 지나면 블랙리스트 레코드를 정리해도 된다.
	 */
	public TokenRefreshResult rotateRefreshToken(Long userId, String jti, Instant refreshExpiresAt) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
		if (user.isWithdrawn()) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		if (user.getStatus() == UserStatus.BLACKLISTED) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED);
		}
		if (tokenBlacklistRepository.existsByJti(jti)) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		LocalDateTime expiresAt = LocalDateTime.ofInstant(refreshExpiresAt, clock.getZone());
		tokenBlacklistRepository.saveAndFlush(
				TokenBlacklist.of(userId, jti, BlacklistReason.REFRESH_ROTATED, expiresAt));

		String role = user.getRole().name();
		String accessToken = jwtTokenProvider.createAccessToken(userId, role);
		String refreshToken = jwtTokenProvider.createRefreshToken(userId, role);
		return new TokenRefreshResult(TokenRefreshResponse.of(accessToken), refreshToken);
	}

	private User register(OAuthUserInfo userInfo) {
		Image profileImage = createImageOrNull(userInfo.profileImageUrl());
		User user = User.register(userInfo.nickname(), userInfo.email(), profileImage, userInfo.provider());
		return userRepository.save(user);
	}

	private User updateExisting(User user, OAuthUserInfo userInfo) {
		// email 이 유일 식별키다. 같은 이메일이라도 최초 가입과 다른 provider 로 로그인하면
		// 기존 계정을 덮어쓰지 않고 차단한다(다른 소셜로 가입 시도 → 409).
		if (user.getProvider() != userInfo.provider()) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
		}

		// 재로그인 시 소셜 프로필(닉네임 + 이미지)을 동기화한다.
		Image current = user.getProfileImage();
		Image profileImage = syncImage(current, userInfo.profileImageUrl());
		user.updateProfile(userInfo.nickname(), profileImage);

		// 프로필 이미지가 제거된 경우(새 url 없음 + 기존 이미지 존재): users.image_id 해제만으론 images row 가 고아로 남는다.
		// User 에 orphanRemoval 설정이 없으므로 여기서 기존 row 를 명시적으로 삭제한다.
		// updateProfile 로 FK(image_id)를 먼저 NULL 로 끊은 뒤 삭제하므로 FK 제약 위반은 없다.
		if (profileImage == null && current != null) {
			imageRepository.delete(current);
		}
		return user;
	}

	private Image createImageOrNull(String imageUrl) {
		return StringUtils.hasText(imageUrl) ? imageRepository.save(Image.of(imageUrl)) : null;
	}

	private Image syncImage(Image current, String imageUrl) {
		if (!StringUtils.hasText(imageUrl)) {
			return null;
		}
		if (current != null) {
			current.updateUrl(imageUrl);
			return current;
		}
		return imageRepository.save(Image.of(imageUrl));
	}

}
