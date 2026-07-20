package com.basecamp.backend.security.handler;

import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/**
 * 시큐리티 필터 단계(EntryPoint / AccessDeniedHandler)에서 발생한 예외를 {@link ApiResponse} JSON 형식으로 직렬화해 응답한다.
 *
 * <p>필터 단계 예외는 {@code @RestControllerAdvice}가 처리하지 못하므로 별도 직렬화가 필요하다. 컨트롤러/전역 예외 핸들러와 같은 {@code {
 * success, code, message }} 봉투를 써서 응답 구조를 통일한다.
 */
final class SecurityResponseWriter {

  private SecurityResponseWriter() {}

  static void write(HttpServletResponse response, ErrorCode errorCode, ObjectMapper objectMapper)
      throws IOException {
    response.setStatus(errorCode.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode));
  }
}
