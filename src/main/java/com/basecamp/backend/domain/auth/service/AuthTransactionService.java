package com.basecamp.backend.domain.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.security.JwtTokenProvider;
import com.basecamp.backend.domain.auth.client.OAuthUserInfo;
import com.basecamp.backend.domain.auth.dto.response.LoginResponse;
import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.User;
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
	private final JwtTokenProvider jwtTokenProvider;

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
