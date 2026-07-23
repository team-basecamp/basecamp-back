package com.basecamp.backend.domain.auth.controller;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.auth.dto.request.LoginRequest;
import com.basecamp.backend.domain.auth.dto.request.WithdrawRequest;
import com.basecamp.backend.domain.auth.dto.response.LoginResponse;
import com.basecamp.backend.domain.auth.dto.response.LoginStateResponse;
import com.basecamp.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.basecamp.backend.domain.auth.service.AuthService;
import com.basecamp.backend.domain.auth.service.LoginResult;
import com.basecamp.backend.domain.auth.service.TokenRefreshResult;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.security.jwt.JwtAuthenticationFilter;
import com.basecamp.backend.security.support.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "소셜 로그인 / 토큰")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final CookieUtil cookieUtil;

  /**
   * 네이버 로그인 시작. 서버가 서명한 state 를 발급한다(CSRF 방지). 프론트는 이 state 로 네이버 authorize 를 요청하고, 콜백에서 되돌아온 state
   * 를 {@code /login/naver} 요청에 그대로 실어 보낸다.
   */
  @Operation(
      summary = "네이버 로그인 state 발급",
      description =
          "네이버 로그인 시작 시 호출. 서버가 HMAC 서명한 무상태 state 를 발급한다(CSRF 방지). "
              + "프론트는 이 값을 네이버 authorize 의 state 로 사용하고, 콜백에서 그대로 되돌려준다. "
              + "state 는 서명·만료(5분)를 서버가 검증한다.")
  @GetMapping("/login/naver/state")
  public ResponseEntity<LoginStateResponse> issueNaverLoginState() {
    return ResponseEntity.ok(new LoginStateResponse(authService.issueNaverLoginState()));
  }

  /**
   * 소셜 로그인(코드 릴레이). provider(kakao/google/naver)는 경로로, 인가 코드는 body 로 받는다. access token 은 응답 body 로,
   * refresh token 은 HttpOnly 쿠키({@code Set-Cookie})로 내려준다.
   */
  @Operation(
      summary = "소셜 로그인(코드 릴레이)",
      description =
          "provider(kakao/google/naver)를 경로로, 인가 코드를 body 로 받아 자체 JWT 를 발급한다. "
              + "access token 은 응답 body, refresh token 은 HttpOnly 쿠키(Set-Cookie)로 내려간다. "
              + "네이버는 body 에 서버가 발급한 state 를 함께 보내야 한다.")
  @PostMapping("/login/{provider}")
  public ResponseEntity<LoginResponse> login(
      @PathVariable String provider, @Valid @RequestBody LoginRequest request) {
    LoginResult result =
        authService.login(parseProvider(provider), request.code(), request.state());
    ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(result.refreshToken());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(result.response());
  }

  /**
   * access 토큰 재발급. refresh 토큰은 body 가 아니라 HttpOnly 쿠키에서 읽으므로 요청 body 가 없다. 회전(rotation)된 새 refresh
   * 토큰이 {@code Set-Cookie} 로 기존 쿠키를 덮어쓴다.
   */
  @Operation(
      summary = "액세스 토큰 재발급",
      description =
          "HttpOnly 쿠키의 refresh token 으로 access token 을 재발급한다. "
              + "이때 refresh token 도 새로 발급(회전)되어 Set-Cookie 로 교체되고, 이전 토큰은 즉시 폐기된다. "
              + "이미 폐기된 토큰으로 요청하면 탈취 의심으로 보아 401 을 반환한다. "
              + "쿠키를 전송하려면 프론트에서 credentials(withCredentials) 옵션이 필요하다.")
  @PostMapping("/token/refresh")
  public ResponseEntity<TokenRefreshResponse> refresh(HttpServletRequest request) {
    TokenRefreshResult result =
        authService.refresh(cookieUtil.resolveRefreshToken(request).orElse(null));
    ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(result.refreshToken());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(result.response());
  }

  /** 로그아웃. refresh 토큰을 폐기하고 쿠키를 삭제한다. access 토큰은 만료 전까지 유효하므로 프론트도 저장소에서 지워야 한다. */
  @Operation(
      summary = "로그아웃",
      description =
          "access token 과 refresh token 을 모두 블랙리스트에 등록해 폐기하고, HttpOnly 쿠키를 삭제한다. "
              + "폐기된 access token 은 인증 필터가 즉시 거부한다. "
              + "쿠키가 없거나 이미 만료된 토큰이어도 성공한다(멱등).")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal AuthUser user, HttpServletRequest request) {
    authService.logout(
        user.id(),
        JwtAuthenticationFilter.resolveBearerToken(request),
        cookieUtil.resolveRefreshToken(request).orElse(null));
    return noContentWithDeletedCookie();
  }

  /** 회원 탈퇴(soft delete). 탈퇴와 refresh 토큰 폐기는 한 트랜잭션으로 처리된다. */
  @Operation(
      summary = "회원 탈퇴",
      description =
          "회원을 탈퇴 처리(soft delete)하고 access/refresh token 을 폐기한 뒤 쿠키를 삭제한다. "
              + "이미 탈퇴한 회원이면 404 를 반환한다.")
  @PostMapping("/withdraw")
  public ResponseEntity<Void> withdraw(
      @AuthenticationPrincipal AuthUser user,
      @Valid @RequestBody WithdrawRequest request,
      HttpServletRequest httpRequest) {
    authService.withdraw(
        user.id(),
        request.reason(),
        JwtAuthenticationFilter.resolveBearerToken(httpRequest),
        cookieUtil.resolveRefreshToken(httpRequest).orElse(null));
    return noContentWithDeletedCookie();
  }

  private ResponseEntity<Void> noContentWithDeletedCookie() {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookieUtil.deleteRefreshTokenCookie().toString())
        .build();
  }

  private Provider parseProvider(String provider) {
    try {
      return Provider.valueOf(provider.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
    }
  }
}
