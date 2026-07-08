package com.basecamp.backend.domain.auth.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;

import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.security.CookieUtil;
import com.basecamp.backend.domain.auth.dto.response.LoginResponse;
import com.basecamp.backend.domain.auth.service.AuthService;
import com.basecamp.backend.domain.auth.service.LoginResult;
import com.basecamp.backend.domain.user.entity.Provider;

/**
 * {@link AuthController} 슬라이스 테스트.
 *
 * <p>실제 소셜 통신/DB는 관심사가 아니므로 {@link AuthService}·{@link CookieUtil}을 목킹하고,
 * 컨트롤러의 provider 파싱·요청 검증·응답(쿠키/body) 조립만 검증한다. 보안 필터는 이 계층의 관심사가
 * 아니므로 {@code addFilters = false}로 비활성화한다.</p>
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AuthService authService;

	@MockBean
	private CookieUtil cookieUtil;

	@Test
	@DisplayName("login_유효한provider와code_200과access는body_refresh는Set-Cookie")
	void login_유효한provider와code_200과access는body_refresh는쿠키() throws Exception {
		// given
		LoginResponse body = new LoginResponse(
				"access-token", "Bearer", 1L, "user@example.com", "camper", "USER", null);
		given(authService.login(eq(Provider.KAKAO), anyString()))
				.willReturn(new LoginResult(body, "refresh-token"));
		ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "refresh-token")
				.httpOnly(true).path("/api/v1/auth").build();
		given(cookieUtil.createRefreshTokenCookie("refresh-token")).willReturn(refreshCookie);

		// when & then
		mockMvc.perform(post("/api/v1/auth/login/{provider}", "kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"auth-code\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access-token"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.email").value("user@example.com"))
				.andExpect(header().exists(HttpHeaders.SET_COOKIE))
				.andExpect(cookie().value("refreshToken", "refresh-token"))
				.andExpect(cookie().httpOnly("refreshToken", true));

		verify(authService).login(Provider.KAKAO, "auth-code");
	}

	@Test
	@DisplayName("login_지원하지않는provider_400과O001")
	void login_지원하지않는provider_400() throws Exception {
		// given: provider 파싱 단계에서 걸러지므로 서비스 호출 이전에 실패한다.

		// when & then
		mockMvc.perform(post("/api/v1/auth/login/{provider}", "facebook")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"auth-code\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_PROVIDER.getCode()));
	}

	@Test
	@DisplayName("login_code누락_400과C001")
	void login_code누락_400() throws Exception {
		// given: @NotBlank 검증 실패 → MethodArgumentNotValidException

		// when & then
		mockMvc.perform(post("/api/v1/auth/login/{provider}", "kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
	}

}
