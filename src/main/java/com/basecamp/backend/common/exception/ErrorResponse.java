package com.basecamp.backend.common.exception;

import java.util.ArrayList;
import java.util.List;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import lombok.Getter;

@Getter
public class ErrorResponse {

	private final String code;
	private final String message;
	private final int status;
	private final List<FieldErrorDetail> errors;

	private ErrorResponse(ErrorCode errorCode, List<FieldErrorDetail> errors) {
		this.code = errorCode.getCode();
		this.message = errorCode.getMessage();
		this.status = errorCode.getStatus().value();
		this.errors = errors;
	}

	private ErrorResponse(ErrorCode errorCode, String message, List<FieldErrorDetail> errors) {
		this.code = errorCode.getCode();
		this.message = message;
		this.status = errorCode.getStatus().value();
		this.errors = errors;
	}

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode, new ArrayList<>());
	}

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(errorCode, message, new ArrayList<>());
	}

	public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
		return new ErrorResponse(errorCode, FieldErrorDetail.of(bindingResult));
	}

	@Getter
	public static class FieldErrorDetail {

		private final String field;
		private final String value;
		private final String reason;

		private FieldErrorDetail(String field, String value, String reason) {
			this.field = field;
			this.value = value;
			this.reason = reason;
		}

		public static List<FieldErrorDetail> of(BindingResult bindingResult) {
			List<FieldError> fieldErrors = bindingResult.getFieldErrors();
			List<FieldErrorDetail> result = new ArrayList<>();
			for (FieldError fieldError : fieldErrors) {
				Object rejectedValue = fieldError.getRejectedValue();
				result.add(new FieldErrorDetail(
						fieldError.getField(),
						rejectedValue == null ? "" : rejectedValue.toString(),
						fieldError.getDefaultMessage()));
			}
			return result;
		}

	}

}
