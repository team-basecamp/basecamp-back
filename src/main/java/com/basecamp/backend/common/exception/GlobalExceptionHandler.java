package com.basecamp.backend.common.exception;

import com.basecamp.backend.common.response.ApiResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 전역 예외 처리. 모든 오류를 {@link ApiResponse} 봉투(success=false)로 변환해, 정상 응답과 동일한 구조로 프론트에 내려준다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  /** Bean Validation(@Valid) 실패 — 잘못된 필드를 전부 담아 400 으로 응답한다. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    // ex.getMessage() 에는 거부된 원본 값(비밀번호·토큰 등)이 섞일 수 있어, 실패한 필드명만 남긴다.
    List<String> invalidFields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getField)
            .distinct()
            .toList();
    log.warn("Validation failed for fields: {}", invalidFields);
    ApiResponse<Void> body =
        ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, ex.getBindingResult());
    return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus()).body(body);
  }

  @ExceptionHandler(BusinessException.class)
  protected ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
    log.warn("BusinessException: {}", ex.getMessage());
    ErrorCode errorCode = ex.getErrorCode();
    return ResponseEntity.status(errorCode.getStatus())
        .body(ApiResponse.error(errorCode, ex.getMessage()));
  }

  /**
   * {@code ?sort=존재하지않는필드} 처럼 Pageable 의 정렬 대상이 엔티티에 없을 때 Spring Data 가 던진다. 클라이언트가 고칠 수 있는 잘못된
   * 요청이므로 500 이 아니라 400 으로 돌려준다.
   */
  @ExceptionHandler(PropertyReferenceException.class)
  protected ResponseEntity<ApiResponse<Void>> handlePropertyReferenceException(
      PropertyReferenceException ex) {
    log.warn("PropertyReferenceException: {}", ex.getMessage());
    return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
        .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE));
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  protected ResponseEntity<ApiResponse<Void>> handleObjectOptimisticLockingFailureException(
      ObjectOptimisticLockingFailureException ex) {
    log.warn("ObjectOptimisticLockingFailureException: {}", ex.getMessage());
    return ResponseEntity.status(ErrorCode.CONCURRENT_MODIFICATION.getStatus())
        .body(ApiResponse.error(ErrorCode.CONCURRENT_MODIFICATION));
  }

  /**
   * DB 유니크·FK 등 무결성 제약 위반. 서비스에서 명시적 {@link ErrorCode} 로 변환하지 못하고 커밋 시점에 새어 나온 경우의 안전망이다. 클라이언트 데이터
   * 충돌에 가까우므로 500 이 아니라 409 로 돌려준다.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  protected ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
      DataIntegrityViolationException ex) {
    log.warn("DataIntegrityViolationException: {}", ex.getMessage());
    return ResponseEntity.status(ErrorCode.DATA_INTEGRITY_VIOLATION.getStatus())
        .body(ApiResponse.error(ErrorCode.DATA_INTEGRITY_VIOLATION));
  }

  @ExceptionHandler(AccessDeniedException.class)
  protected ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
      AccessDeniedException ex) {
    log.warn("AccessDeniedException: {}", ex.getMessage());
    return ResponseEntity.status(ErrorCode.ACCESS_DENIED.getStatus())
        .body(ApiResponse.error(ErrorCode.ACCESS_DENIED));
  }

  /** 업로드 파일 용량이 {@code spring.servlet.multipart} 상한을 넘겼을 때 — 413 으로 봉투에 담아 알려준다. */
  @Override
  protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    log.warn("MaxUploadSizeExceededException: {}", ex.getMessage());
    ApiResponse<Void> body = ApiResponse.error(ErrorCode.IMAGE_SIZE_EXCEEDED);
    return handleExceptionInternal(
        ex, body, headers, ErrorCode.IMAGE_SIZE_EXCEEDED.getStatus(), request);
  }

  @ExceptionHandler(Exception.class)
  protected ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
    // 서버 내부 오류는 원인 추적을 위해 스택 트레이스까지 남긴다.
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
        .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
  }

  /**
   * {@link ResponseEntityExceptionHandler} 가 기본 처리하는 스프링 MVC 예외 (지원하지 않는 메서드, 읽을 수 없는 본문, 필수 파라미터
   * 누락 등)도 기본 ProblemDetail 대신 {@link ApiResponse} 봉투로 통일한다.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    // 위 handleMethodArgumentNotValid 처럼 이미 우리가 봉투로 만든 경우는 그대로 둔다.
    if (body instanceof ApiResponse) {
      return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
    log.warn("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
    ApiResponse<Void> wrapped = ApiResponse.error(resolveErrorCode(statusCode));
    return super.handleExceptionInternal(ex, wrapped, headers, statusCode, request);
  }

  private ErrorCode resolveErrorCode(HttpStatusCode statusCode) {
    if (statusCode.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
      return ErrorCode.METHOD_NOT_ALLOWED;
    }
    if (statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
      return ErrorCode.ENTITY_NOT_FOUND;
    }
    if (statusCode.is4xxClientError()) {
      return ErrorCode.INVALID_INPUT_VALUE;
    }
    return ErrorCode.INTERNAL_SERVER_ERROR;
  }
}
