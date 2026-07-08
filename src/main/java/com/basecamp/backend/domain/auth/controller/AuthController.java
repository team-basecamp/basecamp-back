package com.basecamp.backend.domain.auth.controller;

import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.security.CookieUtil;
import com.basecamp.backend.domain.auth.dto.request.LoginRequest;
import com.basecamp.backend.domain.auth.dto.response.LoginResponse;
import com.basecamp.backend.domain.auth.service.AuthService;
import com.basecamp.backend.domain.auth.service.LoginResult;
import com.basecamp.backend.domain.user.entity.Provider;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final CookieUtil cookieUtil;

	/**
	 * 소셜 로그인(코드 릴레이). provider(kakao/google/naver)는 경로로, 인가 코드는 body 로 받는다.
	 * access token 은 응답 body 로, refresh token 은 HttpOnly 쿠키({@code Set-Cookie})로 내려준다.
	 */
	@PostMapping("/login/{provider}")
	public ResponseEntity<LoginResponse> login(
			@PathVariable String provider,
			@Valid @RequestBody LoginRequest request) {
		LoginResult result = authService.login(parseProvider(provider), request.code());
		ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(result.refreshToken());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
				.body(result.response());
	}

	private Provider parseProvider(String provider) {
		try {
			return Provider.valueOf(provider.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
		}
	}

}
