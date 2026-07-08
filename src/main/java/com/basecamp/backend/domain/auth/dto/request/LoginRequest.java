package com.basecamp.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청 body. 프론트가 소셜에서 받은 인가 코드를 전달한다.
 *
 * <p>{@code state} 는 네이버 로그인의 CSRF 방지 값으로, 네이버 토큰 교환에만 필요하다.
 * 카카오/구글은 보내지 않으므로 선택 필드다.</p>
 */
public record LoginRequest(
		@NotBlank(message = "인가 코드(code)는 필수입니다.")
		String code,

		String state) {
}
