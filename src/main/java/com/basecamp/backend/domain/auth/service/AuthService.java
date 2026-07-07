package com.basecamp.backend.domain.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.security.JwtTokenProvider;
import com.basecamp.backend.domain.auth.client.OAuthUserInfo;
import com.basecamp.backend.domain.auth.client.SocialClientResolver;
import com.basecamp.backend.domain.auth.dto.response.LoginResponse;
import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.ImageRepository;
import com.basecamp.backend.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 소셜 로그인 공통 오케스트레이션.
 *
 * <p>{@code code → (provider별) 토큰교환 + userinfo → email 검증 → email 기준 upsert → 자체 JWT 발급} 흐름을 담당한다.
 * provider별 통신은 {@link SocialClientResolver} 가 선택하는 {@code SocialClient} 에 위임한다.</p>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

	private final SocialClientResolver socialClientResolver;
	private final UserRepository userRepository;
	private final ImageRepository imageRepository;
	private final JwtTokenProvider jwtTokenProvider;

	public LoginResult login(Provider provider, String authorizationCode) {
		OAuthUserInfo userInfo = socialClientResolver.resolve(provider).fetchUserInfo(authorizationCode);

		// #23 정책: 이메일이 회원 식별의 유일 키다. 미제공(동의 안 함) 시 로그인/가입을 거부한다.
		if (!StringUtils.hasText(userInfo.email())) {
			throw new BusinessException(ErrorCode.EMAIL_CONSENT_REQUIRED);
		}

		User user = userRepository.findByEmail(userInfo.email())
				.map(existing -> updateExisting(existing, userInfo))
				.orElseGet(() -> register(userInfo));

		String role = user.getRole().name();
		String accessToken = jwtTokenProvider.createAccessToken(user.getId(), role);
		String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), role);

		// LAZY 연관 접근이 필요하므로 응답 body 는 트랜잭션 내부에서 조립한다.
		return new LoginResult(LoginResponse.from(user, accessToken), refreshToken);
	}

	private User register(OAuthUserInfo userInfo) {
		Image profileImage = createImageOrNull(userInfo.profileImageUrl());
		User user = User.register(userInfo.nickname(), userInfo.email(), profileImage, userInfo.provider());
		return userRepository.save(user);
	}

	private User updateExisting(User user, OAuthUserInfo userInfo) {
		// 재로그인 시 소셜 프로필(닉네임 + 이미지)을 동기화한다.
		Image profileImage = syncImage(user.getProfileImage(), userInfo.profileImageUrl());
		user.updateProfile(userInfo.nickname(), profileImage);
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
