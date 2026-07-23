package com.basecamp.backend.common.response;

import com.basecamp.backend.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import org.springframework.validation.BindingResult;

/**
 * 모든 REST 응답의 공통 봉투(envelope).
 *
 * <p>정상 응답과 오류 응답이 같은 구조를 갖게 해, 프론트엔드가 {@code response.success} 하나로 성공/실패를 분기할 수 있게 한다.
 *
 * <ul>
 *   <li>성공: {@code { "success": true, "message": "OK", "data": ... }}
 *   <li>실패: {@code { "success": false, "code": "R002", "message": "...", "errors": [...] }}
 * </ul>
 *
 * <p>null 필드는 직렬화에서 제외된다({@link JsonInclude}). 따라서 성공 응답에는 {@code code}/{@code errors} 가, 데이터 없는 오류
 * 응답에는 {@code data} 가 나타나지 않는다.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private final boolean success;
  private final String code;
  private final String message;
  private final T data;
  private final List<FieldErrorDetail> errors;

  private ApiResponse(
      boolean success, String code, String message, T data, List<FieldErrorDetail> errors) {
    this.success = success;
    this.code = code;
    this.message = message;
    this.data = data;
    this.errors = errors;
  }

  // ── 성공 ──────────────────────────────────────────────────────────

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, null, "OK", data, null);
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(true, null, message, data, null);
  }

  // ── 실패 ──────────────────────────────────────────────────────────

  public static ApiResponse<Void> error(ErrorCode errorCode) {
    return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null, null);
  }

  public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
    return new ApiResponse<>(false, errorCode.getCode(), message, null, null);
  }

  /**
   * Bean Validation 실패 응답. 잘못된 필드를 {@code findFirst} 하지 않고 전부 담아, 프론트가 어떤 필드들이 틀렸는지 한 번에 보여줄 수 있게
   * 한다.
   */
  public static ApiResponse<Void> error(ErrorCode errorCode, BindingResult bindingResult) {
    return new ApiResponse<>(
        false,
        errorCode.getCode(),
        errorCode.getMessage(),
        null,
        FieldErrorDetail.from(bindingResult));
  }

  /** 검증 실패 항목. 비밀번호·토큰 등 민감한 입력이 섞일 수 있는 거부된 원본 값(rejectedValue)은 응답에 담지 않고, 필드명과 검증 메시지만 노출한다. */
  @Getter
  public static class FieldErrorDetail {

    private final String field;
    private final String reason;

    private FieldErrorDetail(String field, String reason) {
      this.field = field;
      this.reason = reason;
    }

    private static List<FieldErrorDetail> from(BindingResult bindingResult) {
      return bindingResult.getFieldErrors().stream()
          .map(
              fieldError ->
                  new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()))
          .toList();
    }
  }
}
