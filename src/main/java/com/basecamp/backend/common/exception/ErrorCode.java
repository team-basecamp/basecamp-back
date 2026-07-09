package com.basecamp.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

	// Common
	INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "유효하지 않은 입력값입니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "허용되지 않은 HTTP 메서드입니다."),
	ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "요청한 리소스를 찾을 수 없습니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C004", "서버 내부 오류가 발생했습니다."),

	// Auth / Security
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 토큰입니다."),
	EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "만료된 토큰입니다."),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "A004", "접근 권한이 없습니다."),
	REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A005", "refresh 토큰이 없습니다. 다시 로그인해 주세요."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A006", "유효하지 않은 refresh 토큰입니다. 다시 로그인해 주세요."),

	// Reservation
	INVALID_RESERVATION_PERIOD(HttpStatus.BAD_REQUEST, "R001", "체크아웃 날짜는 체크인 날짜보다 이후여야 합니다."),
	RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R002", "예약 정보가 존재하지 않습니다."),
	ALREADY_CANCELED_OR_REJECTED(HttpStatus.BAD_REQUEST, "R003", "이미 취소 되었거나 거절된 예약입니다."),
	RESERVATION_NOT_PENDING(HttpStatus.BAD_REQUEST, "R004", "대기 중인 예약만 수락/거절할 수 있습니다."),

	// OAuth / Social Login
	UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "O001", "지원하지 않는 소셜 로그인 제공자입니다."),
	EMAIL_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "O002", "이메일 제공에 동의해야 로그인/회원가입이 가능합니다."),
	SOCIAL_TOKEN_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "O003", "소셜 토큰 발급에 실패했습니다."),
	SOCIAL_USERINFO_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "O004", "소셜 사용자 정보 조회에 실패했습니다."),
	INVALID_OAUTH_STATE(HttpStatus.BAD_REQUEST, "O005", "유효하지 않은 로그인 요청입니다(state 검증 실패)."),
	EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "O006", "이미 다른 소셜 계정으로 가입된 이메일입니다. 기존 로그인 방식을 이용해 주세요."),

	// User
	INVALID_EMAIL(HttpStatus.BAD_REQUEST, "U001", "유효하지 않은 이메일입니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U002", "회원 정보를 찾을 수 없습니다."),

	// Camp
	CAMP_NOT_FOUND(HttpStatus.NOT_FOUND, "CP001", "캠핑장을 찾을 수 없습니다."),
	;

	private final HttpStatus status;
	private final String code;
	private final String message;

	ErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

}
