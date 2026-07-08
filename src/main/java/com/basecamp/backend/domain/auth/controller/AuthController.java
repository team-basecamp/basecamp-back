package com.basecamp.backend.domain.auth.controller;

import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.basecamp.backend.domain.auth.dto.response.LoginStateResponse;
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
	 * 네이버 로그인 시작. 서버가 서명한 state 를 발급한다(CSRF 방지). 프론트는 이 state 로 네이버 authorize 를 요청하고,
	 * 콜백에서 되돌아온 state 를 {@code /login/naver} 요청에 그대로 실어 보낸다.
	 */
	@GetMapping("/login/naver/state")
	public ResponseEntity<LoginStateResponse> issueNaverLoginState() {
		return ResponseEntity.ok(new LoginStateResponse(authService.issueNaverLoginState()));
	}

	/**
	 * 소셜 로그인(코드 릴레이). provider(kakao/google/naver)는 경로로, 인가 코드는 body 로 받는다.
	 * access token 은 응답 body 로, refresh token 은 HttpOnly 쿠키({@code Set-Cookie})로 내려준다.
	 */
	@PostMapping("/login/{provider}")
	public ResponseEntity<LoginResponse> login(
			@PathVariable String provider,
			@Valid @RequestBody LoginRequest request) {
		LoginResult result = authService.login(parseProvider(provider), request.code(), request.state());
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
