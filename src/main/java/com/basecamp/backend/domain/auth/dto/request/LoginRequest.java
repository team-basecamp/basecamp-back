package com.basecamp.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청 body. 프론트가 소셜에서 받은 인가 코드를 전달한다.
 */
public record LoginRequest(
		@NotBlank(message = "인가 코드(code)는 필수입니다.")
		String code) {
}
