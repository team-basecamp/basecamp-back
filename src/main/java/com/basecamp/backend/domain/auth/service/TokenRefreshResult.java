package com.basecamp.backend.domain.auth.service;

import com.basecamp.backend.domain.auth.dto.response.TokenRefreshResponse;

/** 토큰 재발급 결과. 응답 body 와, 쿠키로 내려갈 새 refresh token(회전 결과)을 함께 나른다. */
public record TokenRefreshResult(TokenRefreshResponse response, String refreshToken) {}
