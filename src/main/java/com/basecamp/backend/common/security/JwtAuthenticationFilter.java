package com.basecamp.backend.common.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.basecamp.backend.common.exception.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 요청 헤더의 {@code Authorization: Bearer <token>} 을 검증해 SecurityContext에 인증을 세팅한다.
 *
 * <p>무상태 인증: DB를 조회하지 않고 access 토큰 클레임(userId, role)만으로 인증을 구성한다.</p>
 * <p>토큰이 없거나 유효하지 않으면 인증을 세팅하지 않고 다음 필터로 넘긴다. 최종 인가 실패는
 * {@link JwtAuthenticationEntryPoint}가 처리하며, 이때 참고할 에러 코드를 요청 속성에 담아둔다.</p>
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	public static final String AUTHORIZATION_HEADER = "Authorization";
	public static final String BEARER_PREFIX = "Bearer ";
	public static final String ATTR_ERROR_CODE = "jwtErrorCode";

	private final JwtTokenProvider jwtTokenProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String token = resolveToken(request);
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
			Long userId = jwtTokenProvider.getUserId(claims);
			String role = jwtTokenProvider.getRole(claims);

			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
					userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (ExpiredJwtException e) {
			request.setAttribute(ATTR_ERROR_CODE, ErrorCode.EXPIRED_TOKEN);
		} catch (JwtException | IllegalArgumentException e) {
			request.setAttribute(ATTR_ERROR_CODE, ErrorCode.INVALID_TOKEN);
		}
	}

	private String resolveToken(HttpServletRequest request) {
		String header = request.getHeader(AUTHORIZATION_HEADER);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			return header.substring(BEARER_PREFIX.length());
		}
		return null;
	}

}
