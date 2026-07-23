package com.basecamp.backend.security.jwt;

import com.basecamp.backend.common.enums.Role;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.security.cache.TokenBlacklistCache;
import com.basecamp.backend.security.cache.UserRevocationCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 헤더의 {@code Authorization: Bearer <token>} 을 검증해 SecurityContext에 인증을 세팅한다.
 *
 * <p>인증은 access 토큰 클레임(userId, role)만으로 구성하며, principal 로 {@link AuthUser} 를 넣는다. DB는 조회하지 않고, 무효화
 * 여부만 Redis에서 확인한다. 폐기된 토큰({@link TokenBlacklistCache}, 로그아웃·탈퇴, #39)과 제재된 회원({@link
 * UserRevocationCache}, #18)을 만료 전에 거부하기 위함이다.
 *
 * <p>토큰이 없거나 유효하지 않으면 인증을 세팅하지 않고 다음 필터로 넘긴다. 최종 인가 실패는 {@link JwtAuthenticationEntryPoint}가 처리하며,
 * 이때 참고할 에러 코드를 요청 속성에 담아둔다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  public static final String AUTHORIZATION_HEADER = "Authorization";
  public static final String BEARER_PREFIX = "Bearer ";
  public static final String ATTR_ERROR_CODE = "jwtErrorCode";

  private final JwtTokenProvider jwtTokenProvider;
  private final TokenBlacklistCache tokenBlacklistCache;
  private final UserRevocationCache userRevocationCache;

  /**
   * {@code Authorization: Bearer <token>} 헤더에서 토큰을 꺼낸다. 헤더가 없거나 형식이 다르면 {@code null}.
   *
   * <p>로그아웃/탈퇴 컨트롤러도 폐기할 access 토큰을 얻기 위해 같은 규칙이 필요해 공개한다.
   */
  public static String resolveBearerToken(HttpServletRequest request) {
    String header = request.getHeader(AUTHORIZATION_HEADER);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = resolveBearerToken(request);
    if (token != null) {
      authenticate(request, token);
    }
    filterChain.doFilter(request, response);
  }

  private void authenticate(HttpServletRequest request, String token) {
    try {
      Claims claims = jwtTokenProvider.parseClaims(token);
      if (!JwtTokenProvider.TOKEN_TYPE_ACCESS.equals(jwtTokenProvider.getType(claims))) {
        // refresh 토큰 등 access 가 아닌 토큰으로는 인증 불가
        request.setAttribute(ATTR_ERROR_CODE, ErrorCode.INVALID_TOKEN);
        return;
      }

      String subject = claims.getSubject();
      String role = jwtTokenProvider.getRole(claims);
      // 필수 클레임(userId, role)이 없거나 공백이면 인증하지 않는다.
      // (role 누락 시 "ROLE_null"/"ROLE_" 권한으로 인증되는 것을 방지)
      if (!StringUtils.hasText(subject) || !StringUtils.hasText(role)) {
        request.setAttribute(ATTR_ERROR_CODE, ErrorCode.INVALID_TOKEN);
        return;
      }
      // jti 없는 토큰(#39 이전 발급)은 폐기 대상을 특정할 수 없어 영원히 무효화할 수 없다. 재로그인을 유도한다.
      String jti = jwtTokenProvider.getJti(claims);
      if (!StringUtils.hasText(jti)) {
        request.setAttribute(ATTR_ERROR_CODE, ErrorCode.INVALID_TOKEN);
        return;
      }
      // 로그아웃·탈퇴로 폐기된 토큰. Redis 조회 실패 시에는 통과한다(fail-open).
      if (tokenBlacklistCache.isBlacklisted(jti)) {
        request.setAttribute(ATTR_ERROR_CODE, ErrorCode.INVALID_TOKEN);
        return;
      }

      Long userId = Long.valueOf(subject);
      // 알 수 없는 role 문자열이면 IllegalArgumentException 이 나고 아래에서 INVALID_TOKEN 으로 잡힌다.
      // (Role 로 좁혀두면 "ROLE_HACKER" 같은 임의 권한이 authorities 에 실리지 않는다)
      Role roleValue = Role.valueOf(role);

      // 무효화된 회원 토큰. 토큰 서명은 멀쩡하므로 회원 식별자 + 발급 시각으로 확인한다(#18 제재, #53 승격).
      // 무효화 시각 이전 발급 토큰만 거부하므로, 승격 후 재로그인해 받은 새 토큰은 통과한다(#119). 403 으로 응답한다.
      if (userRevocationCache.isRevoked(userId, jwtTokenProvider.getIssuedAt(claims))) {
        request.setAttribute(ATTR_ERROR_CODE, ErrorCode.BLACKLISTED_USER);
        return;
      }

      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(
              new AuthUser(userId, roleValue),
              null,
              List.of(new SimpleGrantedAuthority("ROLE_" + roleValue.name())));
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (ExpiredJwtException e) {
      request.setAttribute(ATTR_ERROR_CODE, ErrorCode.EXPIRED_TOKEN);
    } catch (JwtException | IllegalArgumentException e) {
      request.setAttribute(ATTR_ERROR_CODE, ErrorCode.INVALID_TOKEN);
    }
  }
}
