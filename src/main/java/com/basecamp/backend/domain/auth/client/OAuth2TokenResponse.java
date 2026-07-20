package com.basecamp.backend.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** OAuth2 토큰 엔드포인트 응답(공통). 필요한 필드만 매핑한다(그 외는 무시). */
public record OAuth2TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType) {}
