package com.basecamp.backend.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.user.dto.request.UpdateProfileRequest;
import com.basecamp.backend.domain.user.dto.response.MyProfileResponse;
import com.basecamp.backend.domain.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 회원 본인의 프로필 조회 · 수정. {@code /api/v1/users/me} 는 로그인 회원만 접근한다(SecurityConfig).
 */
@Tag(name = "User", description = "회원 - 내 프로필 조회/수정")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@Operation(summary = "내 프로필 조회",
			description = "로그인한 회원의 닉네임 · 이메일 · 프로필 이미지 · 권한 등을 조회한다.")
	@GetMapping("/me")
	public ResponseEntity<MyProfileResponse> getMyProfile(@AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.ok(userService.getMyProfile(user.id()));
	}

	@Operation(summary = "내 프로필 수정",
			description = "닉네임과 프로필 이미지를 수정한다. profileImageUrl 을 비우면 이미지를 제거한다. "
					+ "이메일 · 소셜 제공자 · 권한은 바꿀 수 없다.")
	@PostMapping("/me")
	public ResponseEntity<MyProfileResponse> updateMyProfile(
			@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody UpdateProfileRequest request) {
		return ResponseEntity.ok(userService.updateMyProfile(user.id(), request));
	}

}
