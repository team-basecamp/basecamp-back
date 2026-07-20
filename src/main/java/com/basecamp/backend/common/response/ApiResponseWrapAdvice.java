package com.basecamp.backend.common.response;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 도메인 컨트롤러({@code com.basecamp.backend.domain})의 모든 정상 응답 본문을 {@link ApiResponse} 봉투로 자동으로 감싼다.
 *
 * <p>덕분에 각 컨트롤러는 순수 DTO 만 반환하고, 오류 응답({@code GlobalExceptionHandler})과 동일한 {@code { success,
 * message, data }} 구조가 전 엔드포인트에 보장된다.
 *
 * <ul>
 *   <li>springdoc(Swagger) 컨트롤러는 basePackages 밖이라 감싸지 않는다 → API 문서가 깨지지 않는다.
 *   <li>이미 {@link ApiResponse} 인 본문(예외 핸들러 출력·수동 래핑)은 그대로 통과시킨다.
 *   <li>본문이 {@code null}(204 No Content)이면 감싸지 않아 응답 본문을 만들지 않는다.
 * </ul>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.basecamp.backend.domain")
@RequiredArgsConstructor
public class ApiResponseWrapAdvice implements ResponseBodyAdvice<Object> {

  private final ObjectMapper objectMapper;

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    // 이미 ApiResponse 를 직접 반환하는 핸들러는 건드리지 않는다.
    return !ApiResponse.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {

    // 204 No Content 등 본문이 없는 응답은 그대로 둔다(봉투를 씌우면 스펙 위반).
    if (body == null) {
      return null;
    }
    // 예외 핸들러가 만든 오류 봉투 등 이미 감싼 응답은 통과.
    if (body instanceof ApiResponse) {
      return body;
    }
    // String 은 StringHttpMessageConverter 가 처리하므로 객체를 돌려주면 ClassCastException 이 난다.
    // JSON 문자열로 직접 직렬화해서 돌려주고 Content-Type 을 JSON 으로 맞춘다.
    if (body instanceof String) {
      response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
      try {
        return objectMapper.writeValueAsString(ApiResponse.success(body));
      } catch (JsonProcessingException e) {
        // BusinessException 은 원인(cause)을 담지 못하므로, 추적을 위해 여기서 원본을 남긴다.
        log.error("응답 직렬화 실패", e);
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
      }
    }
    return ApiResponse.success(body);
  }
}
