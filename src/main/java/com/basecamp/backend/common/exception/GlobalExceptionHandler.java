package com.basecamp.backend.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		log.warn("MethodArgumentNotValidException: {}", ex.getMessage());
		ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, ex.getBindingResult());
		return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus()).body(response);
	}

	@ExceptionHandler(BusinessException.class)
	protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
		log.warn("BusinessException: {}", ex.getMessage());
		ErrorCode errorCode = ex.getErrorCode();
		ErrorResponse response = ErrorResponse.of(errorCode, ex.getMessage());
		return ResponseEntity.status(errorCode.getStatus()).body(response);
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	protected ResponseEntity<ErrorResponse> handleObjectOptimisticLockingFailureException(ObjectOptimisticLockingFailureException ex) {
		log.warn("ObjectOptimisticLockingFailureException: {}", ex.getMessage());
		ErrorResponse response = ErrorResponse.of(ErrorCode.CONCURRENT_MODIFICATION);
		return ResponseEntity.status(ErrorCode.CONCURRENT_MODIFICATION.getStatus()).body(response);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	protected ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		log.warn("DataIntegrityViolationException: {}", ex.getMessage());
		ErrorResponse response = ErrorResponse.of(ErrorCode.ALREADY_PAID);
		return ResponseEntity.status(ErrorCode.ALREADY_PAID.getStatus()).body(response);
	}

	@ExceptionHandler(AccessDeniedException.class)
	protected ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
		log.warn("AccessDeniedException: {}", ex.getMessage());
		ErrorResponse response = ErrorResponse.of(ErrorCode.ACCESS_DENIED);
		return ResponseEntity.status(ErrorCode.ACCESS_DENIED.getStatus()).body(response);
	}

	@ExceptionHandler(Exception.class)
	protected ResponseEntity<ErrorResponse> handleException(Exception ex) {
		log.error("Unhandled exception", ex);
		ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);
		return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus()).body(response);
	}

}
