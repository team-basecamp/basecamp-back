package com.basecamp.backend.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.basecamp.backend.common.enums.Role;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.security.cache.TokenBlacklistCache;
import com.basecamp.backend.security.cache.UserRevocationCache;
import com.basecamp.backend.security.config.JwtProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/** {@link JwtAuthenticationFilter} 단위 테스트. Step 8 의 핵심인 "폐기된 access 토큰 즉시 거부"를 검증한다. */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  private static final String SECRET = "test-secret-key-for-jwt-authentication-filter-test";
  private static final Long USER_ID = 7L;
  private static final String ROLE = "CUSTOMER";

  @Mock private TokenBlacklistCache tokenBlacklistCache;

  @Mock private UserRevocationCache userRevocationCache;

  private JwtTokenProvider jwtTokenProvider;
  private JwtAuthenticationFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MockFilterChain chain;

  @BeforeEach
  void setUp() {
    jwtTokenProvider = new JwtTokenProvider(new JwtProperties(SECRET, 1_800_000L, 1_209_600_000L));
    filter =
        new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistCache, userRevocationCache);
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void withBearer(String token) {
    request.addHeader(
        JwtAuthenticationFilter.AUTHORIZATION_HEADER,
        JwtAuthenticationFilter.BEARER_PREFIX + token);
  }

  private Object errorCode() {
    return request.getAttribute(JwtAuthenticationFilter.ATTR_ERROR_CODE);
  }

  @Test
  @DisplayName("doFilter_유효한access토큰_AuthUser를principal로_인증한다")
  void doFilter_유효한access토큰_AuthUser를principal로_인증한다() throws Exception {
    // given
    withBearer(jwtTokenProvider.createAccessToken(USER_ID, ROLE));
    given(tokenBlacklistCache.isBlacklisted(org.mockito.ArgumentMatchers.anyString()))
        .willReturn(false);

    // when
    filter.doFilter(request, response, chain);

    // then: principal 은 컨트롤러가 @AuthenticationPrincipal 로 받는 값이다. 타입이 어긋나면 조용히 null 이 되므로 고정한다.
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(new AuthUser(USER_ID, Role.CUSTOMER));
    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_CUSTOMER");
    assertThat(errorCode()).isNull();
  }

  @Test
  @DisplayName("doFilter_알수없는role_인증하지않고_INVALID_TOKEN을남긴다")
  void doFilter_알수없는role_인증하지않고_INVALID_TOKEN을남긴다() throws Exception {
    // given: 서명은 유효하지만 role 클레임이 Role enum 에 없는 값이다.
    //        Role 로 좁혀두지 않으면 "ROLE_HACKER" 라는 임의 권한이 authorities 에 실린다.
    withBearer(jwtTokenProvider.createAccessToken(USER_ID, "HACKER"));

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(errorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("doFilter_폐기된access토큰_인증하지않고_INVALID_TOKEN을남긴다")
  void doFilter_폐기된access토큰_인증하지않고_INVALID_TOKEN을남긴다() throws Exception {
    // given: 로그아웃/탈퇴로 블랙리스트에 올라간 토큰. 서명·만료는 여전히 유효하다.
    withBearer(jwtTokenProvider.createAccessToken(USER_ID, ROLE));
    given(tokenBlacklistCache.isBlacklisted(org.mockito.ArgumentMatchers.anyString()))
        .willReturn(true);

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(errorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("doFilter_Redis장애_failopen으로_인증을통과시킨다")
  void doFilter_Redis장애_failopen으로_인증을통과시킨다() throws Exception {
    // given: 캐시가 fail-open 으로 false 를 반환한다(TokenBlacklistCache 가 예외를 흡수).
    withBearer(jwtTokenProvider.createAccessToken(USER_ID, ROLE));
    given(tokenBlacklistCache.isBlacklisted(org.mockito.ArgumentMatchers.anyString()))
        .willReturn(false);

    // when
    filter.doFilter(request, response, chain);

    // then: Redis 가 죽었다고 전체 인증이 막히면 안 된다.
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
  }

  @Test
  @DisplayName("doFilter_제재된회원_인증하지않고_BLACKLISTED_USER를남긴다")
  void doFilter_제재된회원_인증하지않고_BLACKLISTED_USER를남긴다() throws Exception {
    // given: 토큰 자체는 폐기되지 않았지만(jti 블랙리스트에 없음) 회원이 관리자에게 제재됐다.
    withBearer(jwtTokenProvider.createAccessToken(USER_ID, ROLE));
    given(tokenBlacklistCache.isBlacklisted(org.mockito.ArgumentMatchers.anyString()))
        .willReturn(false);
    given(
            userRevocationCache.isRevoked(
                org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
        .willReturn(true);

    // when
    filter.doFilter(request, response, chain);

    // then: SecurityResponseWriter 가 ErrorCode 의 상태를 그대로 쓰므로 401 이 아니라 403 이 나간다.
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(errorCode()).isEqualTo(ErrorCode.BLACKLISTED_USER);
  }

  @Test
  @DisplayName("doFilter_refresh토큰으로_인증시도_거부한다")
  void doFilter_refresh토큰으로_인증시도_거부한다() throws Exception {
    // given
    withBearer(jwtTokenProvider.createRefreshToken(USER_ID, ROLE));

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(errorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("doFilter_토큰없음_인증도에러코드도없이_통과한다")
  void doFilter_토큰없음_인증도에러코드도없이_통과한다() throws Exception {
    // given: permitAll 엔드포인트는 토큰 없이도 접근 가능해야 한다.

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(errorCode()).isNull();
  }
}
