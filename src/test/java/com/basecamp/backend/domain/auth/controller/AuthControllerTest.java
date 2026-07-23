package com.basecamp.backend.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.basecamp.backend.common.enums.Role;
import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.auth.dto.response.LoginResponse;
import com.basecamp.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.basecamp.backend.domain.auth.service.AuthService;
import com.basecamp.backend.domain.auth.service.LoginResult;
import com.basecamp.backend.domain.auth.service.TokenRefreshResult;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.security.support.CookieUtil;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link AuthController} 슬라이스 테스트.
 *
 * <p>실제 소셜 통신/DB는 관심사가 아니므로 {@link AuthService}·{@link CookieUtil}을 목킹하고, 컨트롤러의 provider 파싱·요청
 * 검증·응답(쿠키/body) 조립만 검증한다. 보안 필터는 이 계층의 관심사가 아니므로 {@code addFilters = false}로 비활성화한다.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private AuthService authService;

  @MockBean private CookieUtil cookieUtil;

  private static final Long USER_ID = 1L;

  /**
   * 보안 필터를 껐으므로({@code addFilters = false}) {@code @AuthenticationPrincipal} 이 읽을 인증을 직접 세팅한다.
   * principal 타입은 {@code JwtAuthenticationFilter} 와 동일하게 {@link AuthUser} 여야 한다. 타입이 어긋나면 컨트롤러
   * 파라미터에 예외 없이 {@code null} 이 들어와 NPE 로만 드러난다.
   */
  @BeforeEach
  void setUpAuthentication() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthUser(USER_ID, Role.CUSTOMER),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("login_유효한provider와code_200과access는body_refresh는Set-Cookie")
  void login_유효한provider와code_200과access는body_refresh는쿠키() throws Exception {
    // given
    LoginResponse body =
        new LoginResponse("access-token", "Bearer", 1L, "user@example.com", "camper", "USER", null);
    given(authService.login(eq(Provider.KAKAO), anyString(), any()))
        .willReturn(new LoginResult(body, "refresh-token"));
    ResponseCookie refreshCookie =
        ResponseCookie.from("refreshToken", "refresh-token")
            .httpOnly(true)
            .path("/api/v1/auth")
            .build();
    given(cookieUtil.createRefreshTokenCookie("refresh-token")).willReturn(refreshCookie);

    // when & then
    mockMvc
        .perform(
            post("/api/v1/auth/login/{provider}", "kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"auth-code\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access-token"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(header().exists(HttpHeaders.SET_COOKIE))
        .andExpect(cookie().value("refreshToken", "refresh-token"))
        .andExpect(cookie().httpOnly("refreshToken", true));

    verify(authService).login(Provider.KAKAO, "auth-code", null);
  }

  @Test
  @DisplayName("login_지원하지않는provider_400과O001")
  void login_지원하지않는provider_400() throws Exception {
    // given: provider 파싱 단계에서 걸러지므로 서비스 호출 이전에 실패한다.

    // when & then
    mockMvc
        .perform(
            post("/api/v1/auth/login/{provider}", "facebook")
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
    mockMvc
        .perform(
            post("/api/v1/auth/login/{provider}", "kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
  }

  @Test
  @DisplayName("refresh_유효한쿠키_200과새access는body_회전된refresh는Set-Cookie")
  void refresh_유효한쿠키_200과새access는body_회전된refresh는쿠키() throws Exception {
    // given
    given(cookieUtil.resolveRefreshToken(any())).willReturn(Optional.of("old-refresh"));
    given(authService.refresh("old-refresh"))
        .willReturn(new TokenRefreshResult(TokenRefreshResponse.of("new-access"), "new-refresh"));
    ResponseCookie rotated =
        ResponseCookie.from("refreshToken", "new-refresh")
            .httpOnly(true)
            .path("/api/v1/auth")
            .build();
    given(cookieUtil.createRefreshTokenCookie("new-refresh")).willReturn(rotated);

    // when & then
    mockMvc
        .perform(post("/api/v1/auth/token/refresh"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("new-access"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(header().exists(HttpHeaders.SET_COOKIE))
        // 회전: 응답 쿠키는 요청에 실려온 이전 토큰이 아니라 새 토큰이어야 한다.
        .andExpect(cookie().value("refreshToken", "new-refresh"))
        .andExpect(cookie().httpOnly("refreshToken", true));

    verify(authService).refresh("old-refresh");
  }

  @Test
  @DisplayName("refresh_쿠키없음_401과A005")
  void refresh_쿠키없음_401() throws Exception {
    // given: 쿠키를 찾지 못하면 컨트롤러가 null 을 넘기고, 서비스가 A005 로 막는다.
    given(cookieUtil.resolveRefreshToken(any())).willReturn(Optional.empty());
    given(authService.refresh(null))
        .willThrow(new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

    // when & then
    mockMvc
        .perform(post("/api/v1/auth/token/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.REFRESH_TOKEN_NOT_FOUND.getCode()));
  }

  @Test
  @DisplayName("refresh_이미폐기된토큰_401과A006")
  void refresh_이미폐기된토큰_401() throws Exception {
    // given: 회전으로 폐기된 토큰의 재제출(탈취 의심).
    given(cookieUtil.resolveRefreshToken(any())).willReturn(Optional.of("rotated-out"));
    given(authService.refresh("rotated-out"))
        .willThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

    // when & then
    mockMvc
        .perform(post("/api/v1/auth/token/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REFRESH_TOKEN.getCode()));
  }

  @Test
  @DisplayName("logout_204와_만료된refresh쿠키를내려주고_access토큰도_서비스로전달한다")
  void logout_204와_만료된refresh쿠키를내려준다() throws Exception {
    // given
    given(cookieUtil.resolveRefreshToken(any())).willReturn(Optional.of("refresh-token"));
    given(cookieUtil.deleteRefreshTokenCookie()).willReturn(deletedCookie());

    // when & then
    mockMvc
        .perform(
            post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("refreshToken", 0));

    // access 토큰도 폐기해야 인증 필터가 즉시 거부한다.
    verify(authService).logout(USER_ID, "access-token", "refresh-token");
  }

  @Test
  @DisplayName("logout_쿠키와헤더가없어도_204로_멱등하게성공한다")
  void logout_쿠키없어도_204로_멱등하게성공한다() throws Exception {
    // given
    given(cookieUtil.resolveRefreshToken(any())).willReturn(Optional.empty());
    given(cookieUtil.deleteRefreshTokenCookie()).willReturn(deletedCookie());

    // when & then
    mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());

    verify(authService).logout(USER_ID, null, null);
  }

  @Test
  @DisplayName("withdraw_204와_탈퇴사유_토큰들을_서비스로전달한다")
  void withdraw_204와_탈퇴사유를_서비스로전달한다() throws Exception {
    // given
    given(cookieUtil.resolveRefreshToken(any())).willReturn(Optional.of("refresh-token"));
    given(cookieUtil.deleteRefreshTokenCookie()).willReturn(deletedCookie());

    // when & then
    mockMvc
        .perform(
            post("/api/v1/auth/withdraw")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"더 이상 이용하지 않아요\"}"))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("refreshToken", 0));

    verify(authService).withdraw(USER_ID, "더 이상 이용하지 않아요", "access-token", "refresh-token");
  }

  @Test
  @DisplayName("withdraw_사유가500자초과_400과C001")
  void withdraw_사유가500자초과_400() throws Exception {
    // given: @Size(max = 500) 검증 실패 → MethodArgumentNotValidException
    String tooLong = "가".repeat(501);

    // when & then
    mockMvc
        .perform(
            post("/api/v1/auth/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + tooLong + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
  }

  private ResponseCookie deletedCookie() {
    return ResponseCookie.from("refreshToken", "")
        .httpOnly(true)
        .path("/api/v1/auth")
        .maxAge(0)
        .build();
  }
}
