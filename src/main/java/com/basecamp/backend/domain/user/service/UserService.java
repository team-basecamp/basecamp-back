package com.basecamp.backend.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.user.dto.request.UpdateProfileRequest;
import com.basecamp.backend.domain.user.dto.response.MyProfileResponse;
import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.ImageRepository;
import com.basecamp.backend.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원 본인의 프로필 조회 · 수정.
 *
 * <p>로그인한 회원은 정의상 활성 상태다(탈퇴 · 제재 회원은 access 토큰 인증 자체가 막힌다). 그래도 토큰과 DB 상태가
 * 어긋나는 경우를 대비해 조회 대상이 없으면 {@link ErrorCode#USER_NOT_FOUND} 로 방어한다.</p>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final ImageRepository imageRepository;

	/** 내 프로필 조회. LAZY 프로필 이미지는 트랜잭션 안에서 매핑한다. */
	@Transactional(readOnly = true)
	public MyProfileResponse getMyProfile(Long userId) {
		return MyProfileResponse.from(findUser(userId));
	}

	/**
	 * 내 프로필(닉네임 · 프로필 이미지) 수정.
	 *
	 * <p>이미지 URL 이 있으면 기존 이미지 행을 그대로 재사용해 URL 만 갱신하고(없으면 새로 만든다),
	 * 비어 있으면 이미지를 제거한다. 엔티티의 {@code updatedAt} 은 감사(auditing) 리스너가 자동 갱신한다.</p>
	 */
	public MyProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
		User user = findUser(userId);
		user.updateProfile(request.nickname(), resolveProfileImage(user, request.profileImageUrl()));
		return MyProfileResponse.from(user);
	}

	private Image resolveProfileImage(User user, String imageUrl) {
		if (!StringUtils.hasText(imageUrl)) {
			return null; // 이미지 제거
		}
		Image current = user.getProfileImage();
		if (current != null) {
			current.updateUrl(imageUrl); // 기존 행 재사용(고아 행을 남기지 않는다)
			return current;
		}
		return imageRepository.save(Image.of(imageUrl));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}

}
