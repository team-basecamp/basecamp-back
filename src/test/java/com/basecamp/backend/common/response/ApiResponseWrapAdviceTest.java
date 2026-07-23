package com.basecamp.backend.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.basecamp.backend.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link ApiResponseWrapAdvice} 단위 테스트.
 *
 * <p>봉투 계약({@code success}/{@code message}/{@code data}) 자체는 개별 컨트롤러가 아니라 이 advice 의 책임이므로, 여기서 한
 * 번에 못박는다. 각 컨트롤러 슬라이스 테스트는 {@code $.success} 만 확인해 "성공 봉투로 감쌌다"는 경계 사실만 검증한다.
 */
class ApiResponseWrapAdviceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ApiResponseWrapAdvice advice = new ApiResponseWrapAdvice(objectMapper);

  private ServletServerHttpResponse newResponse() {
    return new ServletServerHttpResponse(new MockHttpServletResponse());
  }

  @Test
  @DisplayName("beforeBodyWrite_일반DTO_success는true이고message는OK인봉투로감싼다")
  void beforeBodyWrite_일반DTO_success와OK메시지봉투로감싼다() {
    // given
    Object body = Map.of("nickname", "camper");

    // when
    Object result =
        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, null, newResponse());

    // then
    assertThat(result).isInstanceOf(ApiResponse.class);
    ApiResponse<?> wrapped = (ApiResponse<?>) result;
    assertThat(wrapped.isSuccess()).isTrue();
    assertThat(wrapped.getMessage()).isEqualTo("OK");
    assertThat(wrapped.getCode()).isNull();
    assertThat(wrapped.getData()).isSameAs(body);
  }

  @Test
  @DisplayName("beforeBodyWrite_본문이null_204등은감싸지않고null을반환한다")
  void beforeBodyWrite_null본문_감싸지않는다() {
    // given & when
    Object result =
        advice.beforeBodyWrite(null, null, MediaType.APPLICATION_JSON, null, null, newResponse());

    // then
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("beforeBodyWrite_이미ApiResponse봉투_그대로통과시킨다")
  void beforeBodyWrite_이미봉투인본문_그대로통과() {
    // given: 예외 핸들러가 만든 오류 봉투 등
    ApiResponse<Void> already = ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE);

    // when
    Object result =
        advice.beforeBodyWrite(
            already, null, MediaType.APPLICATION_JSON, null, null, newResponse());

    // then: 다시 감싸지 않고 동일 인스턴스를 반환
    assertThat(result).isSameAs(already);
  }

  @Test
  @DisplayName("beforeBodyWrite_String본문_JSON봉투문자열로직렬화하고ContentType을JSON으로맞춘다")
  void beforeBodyWrite_String본문_JSON봉투로직렬화() throws Exception {
    // given
    ServletServerHttpResponse response = newResponse();

    // when: String 은 StringHttpMessageConverter 가 처리하므로 직접 직렬화한 문자열을 돌려준다.
    Object result =
        advice.beforeBodyWrite("동기화 완료", null, MediaType.TEXT_PLAIN, null, null, response);

    // then
    assertThat(result).isInstanceOf(String.class);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    JsonNode parsed = objectMapper.readTree((String) result);
    assertThat(parsed.get("success").asBoolean()).isTrue();
    assertThat(parsed.get("message").asText()).isEqualTo("OK");
    assertThat(parsed.get("data").asText()).isEqualTo("동기화 완료");
  }

  @Test
  @DisplayName("supports_반환타입이이미ApiResponse_false로감싸지않는다")
  void supports_ApiResponse반환타입_false() throws Exception {
    assertThat(advice.supports(returnTypeOf("apiResponseReturning"), null)).isFalse();
  }

  @Test
  @DisplayName("supports_반환타입이순수DTO_true로감싼다")
  void supports_일반반환타입_true() throws Exception {
    assertThat(advice.supports(returnTypeOf("stringReturning"), null)).isTrue();
  }

  // ── supports() 검증용 더미 반환 타입 ────────────────────────────────

  private MethodParameter returnTypeOf(String methodName) throws NoSuchMethodException {
    return new MethodParameter(getClass().getDeclaredMethod(methodName), -1);
  }

  @SuppressWarnings("unused")
  private ApiResponse<Void> apiResponseReturning() {
    return null;
  }

  @SuppressWarnings("unused")
  private String stringReturning() {
    return null;
  }
}
