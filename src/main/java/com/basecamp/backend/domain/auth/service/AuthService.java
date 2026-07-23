package com.basecamp.backend.domain.auth.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.auth.client.OAuthUserInfo;
import com.basecamp.backend.domain.auth.client.SocialClientResolver;
import com.basecamp.backend.domain.auth.entity.BlacklistReason;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.security.jwt.JwtTokenProvider;
import com.basecamp.backend.security.oauth.OAuthStateProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 소셜 로그인 공통 오케스트레이션.
 *
 * <p>{@code code → (provider별) 토큰교환 + userinfo → email 검증 → email 기준 upsert → 자체 JWT 발급} 흐름을 담당한다.
 *
 * <p><b>트랜잭션 경계:</b> 외부 소셜 제공자 HTTP 호출은 응답 지연 시 DB 커넥션을 오래 점유해 커넥션 풀을 고갈시킬 수 있으므로, 트랜잭션 밖에서 먼저
 * 수행한다. email 검증까지 통과한 뒤의 DB upsert + 토큰 발급만 {@link AuthTransactionService} 의 짧은 트랜잭션으로 분리한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

  private final SocialClientResolver socialClientResolver;
  private final AuthTransactionService authTransactionService;
  private final OAuthStateProvider oAuthStateProvider;
  private final JwtTokenProvider jwtTokenProvider;

  /** 네이버 로그인 시작용 서명 state 를 발급한다. 프론트는 이 state 로 네이버 authorize 를 요청한다. */
  public String issueNaverLoginState() {
    return oAuthStateProvider.issue();
  }

  public LoginResult login(Provider provider, String authorizationCode, String state) {
    // 0) 네이버 CSRF 방지: state 는 서버가 발급한 서명값이어야 한다.
    //    - 빈 값: 잘못된 요청(400). - 서명/만료 검증 실패: 위조·만료된 요청(400).
    //    유효하지 않은 state 를 토큰 교환(502)까지 내려보내지 않고 여기서 막는다.
    if (provider == Provider.NAVER) {
      if (!StringUtils.hasText(state)) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
      }
      if (!oAuthStateProvider.verify(state)) {
        throw new BusinessException(ErrorCode.INVALID_OAUTH_STATE);
      }
    }

    // 1) 외부 소셜 호출 — 트랜잭션 밖(응답 지연이 DB 커넥션 점유로 이어지지 않도록).
    //    state 는 네이버 토큰 교환에만 쓰이며, 나머지 provider 는 무시한다.
    OAuthUserInfo userInfo =
        socialClientResolver.resolve(provider).fetchUserInfo(authorizationCode, state);

    // 2) #23 정책: 이메일이 회원 식별의 유일 키다. 미제공(동의 안 함) 시 로그인/가입을 거부한다.
    if (!StringUtils.hasText(userInfo.email())) {
      throw new BusinessException(ErrorCode.EMAIL_CONSENT_REQUIRED);
    }

    // 3) DB upsert + 토큰 발급 — 짧은 트랜잭션.
    try {
      return authTransactionService.upsertUserAndIssueToken(userInfo);
    } catch (DataIntegrityViolationException e) {
      // 동시 최초 가입 경합: 다른 요청이 먼저 같은 email 로 INSERT 를 커밋하면
      // 활성 회원 유니크 제약(uq_users_email_active)을 위반한다.
      // 승자 row 는 이미 커밋됐으므로, 한 번 재시도하면 조회가 이를 찾아 update 경로로 정상 처리된다.
      return authTransactionService.upsertUserAndIssueToken(userInfo);
    }
  }

  /**
   * refresh 토큰으로 access 토큰을 재발급하고, refresh 토큰도 함께 회전시킨다.
   *
   * <p>검증 순서: 존재 → 서명·만료 → {@code type=refresh} → {@code jti} 보유. 통과한 뒤에야 DB 트랜잭션에 진입한다.
   *
   * @param refreshToken HttpOnly 쿠키에서 꺼낸 값. 쿠키가 없으면 {@code null}
   */
  public TokenRefreshResult refresh(String refreshToken) {
    if (!StringUtils.hasText(refreshToken)) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    Claims claims;
    try {
      claims = jwtTokenProvider.parseClaims(refreshToken);
    } catch (ExpiredJwtException e) {
      throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
    } catch (JwtException | IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // access 토큰을 refresh 로 대신 제출하는 것을 막는다(수명이 짧은 토큰을 무한 연장하는 우회 경로).
    if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(jwtTokenProvider.getType(claims))) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // jti 도입(#39) 이전에 발급된 토큰은 폐기 대상을 특정할 수 없어 회전이 불가능하다. 재로그인을 유도한다.
    String jti = jwtTokenProvider.getJti(claims);
    if (!StringUtils.hasText(jti)) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    try {
      return authTransactionService.rotateRefreshToken(
          jwtTokenProvider.getUserId(claims), jti, jwtTokenProvider.getExpiresAt(claims));
    } catch (DataIntegrityViolationException e) {
      // 같은 refresh 토큰으로 동시에 재발급 요청이 들어와 UNIQUE(jti) 를 위반한 경우.
      // 정상 사용자의 중복 요청일 수도 있으나, 토큰 재사용과 구분할 수 없으므로 보수적으로 거부한다.
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
  }

  /**
   * 로그아웃. access 토큰과 refresh 토큰을 모두 폐기한다. 쿠키 삭제는 컨트롤러가 담당한다.
   *
   * <p>refresh 폐기로 재발급 경로가 끊기고, access 폐기로 {@code JwtAuthenticationFilter} 가 즉시 거부한다. 폐기할 토큰이 하나도
   * 없어도(쿠키 없음, 만료·위조된 토큰) 조용히 성공시킨다(멱등).
   */
  public void logout(Long userId, String accessToken, String refreshToken) {
    List<TokenInfo> tokens = parseTokens(userId, accessToken, refreshToken);
    if (tokens.isEmpty()) {
      return;
    }
    try {
      authTransactionService.blacklistTokens(userId, tokens, BlacklistReason.LOGOUT);
    } catch (DataIntegrityViolationException e) {
      // 동시 로그아웃으로 다른 요청이 먼저 등록했다. 목표 상태(폐기됨)는 이미 달성됐다.
    }
  }

  /** 회원 탈퇴(soft delete) + access/refresh 토큰 폐기. 쿠키 삭제는 컨트롤러가 담당한다. */
  public void withdraw(Long userId, String reason, String accessToken, String refreshToken) {
    List<TokenInfo> tokens = parseTokens(userId, accessToken, refreshToken);
    try {
      authTransactionService.withdrawUser(userId, reason, tokens);
    } catch (DataIntegrityViolationException e) {
      // 토큰 폐기가 UNIQUE(jti) 를 위반해 탈퇴까지 함께 롤백된 경우.
      // 재시도하면 이미 등록된 jti 를 건너뛰고 탈퇴만 처리된다.
      authTransactionService.withdrawUser(userId, reason, tokens);
    }
  }

  /** 폐기 대상 토큰들을 추린다. 폐기할 수 없는 토큰(없음/만료/위조/타인/jti 없음)은 조용히 제외한다. */
  private List<TokenInfo> parseTokens(Long userId, String accessToken, String refreshToken) {
    return Stream.of(
            parseToken(userId, accessToken, JwtTokenProvider.TOKEN_TYPE_ACCESS),
            parseToken(userId, refreshToken, JwtTokenProvider.TOKEN_TYPE_REFRESH))
        .flatMap(Optional::stream)
        .toList();
  }

  /**
   * 토큰에서 폐기에 필요한 정보만 뽑는다. 폐기할 수 없는 토큰이면 {@link Optional#empty()}.
   *
   * <p>로그아웃/탈퇴는 멱등해야 하므로 예외를 던지지 않는다. 만료·위조 토큰은 이미 무력하고, jti 없는 토큰(#39 이전 발급)은 폐기 대상을 특정할 수 없어 그냥
   * 무시한다.
   *
   * @param expectedType {@code access} 인지 {@code refresh} 인지. 종류가 다르면 폐기 대상이 아니다.
   */
  private Optional<TokenInfo> parseToken(Long userId, String token, String expectedType) {
    if (!StringUtils.hasText(token)) {
      return Optional.empty();
    }

    Claims claims;
    try {
      claims = jwtTokenProvider.parseClaims(token);
    } catch (JwtException | IllegalArgumentException e) {
      // ExpiredJwtException 포함. 만료된 토큰은 서명 검증 단계에서 이미 거부되므로 폐기할 필요가 없다.
      return Optional.empty();
    }

    if (!expectedType.equals(jwtTokenProvider.getType(claims))
        || !userId.equals(jwtTokenProvider.getUserId(claims))) {
      // 남의 쿠키/헤더를 실어보내 타인의 토큰을 폐기시키는 것을 막는다(인증된 principal 이 신원의 기준).
      return Optional.empty();
    }

    String jti = jwtTokenProvider.getJti(claims);
    if (!StringUtils.hasText(jti)) {
      return Optional.empty();
    }

    Instant expiresAt = jwtTokenProvider.getExpiresAt(claims);
    return Optional.of(
        JwtTokenProvider.TOKEN_TYPE_ACCESS.equals(expectedType)
            ? TokenInfo.access(jti, expiresAt)
            : TokenInfo.refresh(jti, expiresAt));
  }
}
