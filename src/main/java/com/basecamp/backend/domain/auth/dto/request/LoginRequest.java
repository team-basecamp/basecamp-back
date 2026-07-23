package com.basecamp.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 소셜 로그인 요청 body. 프론트가 소셜에서 받은 인가 코드를 전달한다.
 *
 * <p>{@code state} 는 네이버 로그인의 CSRF 방지 값으로, 네이버 토큰 교환에만 필요하다. 카카오/구글은 보내지 않으므로 선택 필드다. 외부 토큰 교환 폼에
 * 그대로 실리는 값이라 길이 상한을 둔다.
 */
public record LoginRequest(
    @NotBlank(message = "인가 코드(code)는 필수입니다.") String code,
    @Size(max = 255, message = "state 값이 너무 깁니다.") String state) {}
