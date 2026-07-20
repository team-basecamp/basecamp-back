package com.basecamp.backend.security.handler;

import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.security.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 인증되지 않은 사용자가 보호된 리소스에 접근했을 때 401 JSON 응답을 반환한다.
 *
 * <p>{@link JwtAuthenticationFilter}가 요청 속성에 담아둔 에러 코드(만료/유효하지 않음)를 우선 사용하고, 없으면 기본 {@link
 * ErrorCode#UNAUTHORIZED}로 응답한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    ErrorCode errorCode = (ErrorCode) request.getAttribute(JwtAuthenticationFilter.ATTR_ERROR_CODE);
    if (errorCode == null) {
      errorCode = ErrorCode.UNAUTHORIZED;
    }
    SecurityResponseWriter.write(response, errorCode, objectMapper);
  }
}
