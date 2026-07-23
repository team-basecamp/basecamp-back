package com.basecamp.backend.domain.auth.service;

import com.basecamp.backend.domain.auth.dto.response.LoginResponse;

/**
 * 로그인 처리 결과를 컨트롤러로 전달하는 내부 캐리어.
 *
 * <p>{@code response} 는 응답 body 로, {@code refreshToken} 은 컨트롤러가 {@code CookieUtil} 로 HttpOnly 쿠키에
 * 담는다.
 */
public record LoginResult(LoginResponse response, String refreshToken) {}
