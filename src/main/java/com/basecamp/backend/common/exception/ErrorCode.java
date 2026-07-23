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
  CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "C005", "다른 요청에 의해 이미 처리되었습니다. 다시 시도해 주세요."),
  DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "C006", "요청이 데이터 제약 조건을 위반했습니다."),

  // Auth / Security
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 토큰입니다."),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "만료된 토큰입니다."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "A004", "접근 권한이 없습니다."),
  REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A005", "refresh 토큰이 없습니다. 다시 로그인해 주세요."),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A006", "유효하지 않은 refresh 토큰입니다. 다시 로그인해 주세요."),
  BLACKLISTED_USER(HttpStatus.FORBIDDEN, "A007", "제재된 계정입니다. 관리자에게 문의해 주세요."),

  // Reservation
  INVALID_RESERVATION_PERIOD(HttpStatus.BAD_REQUEST, "R001", "체크아웃 날짜는 체크인 날짜보다 이후여야 합니다."),
  RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R002", "예약 정보가 존재하지 않습니다."),
  ALREADY_CANCELED_OR_REJECTED(HttpStatus.BAD_REQUEST, "R003", "이미 취소 되었거나 거절된 예약입니다."),
  RESERVATION_NOT_PENDING(HttpStatus.BAD_REQUEST, "R004", "대기 중인 예약만 수락/거절할 수 있습니다."),
  DUPLICATE_RESERVATION(HttpStatus.CONFLICT, "R005", "이미 같은 예약이 존재합니다."),
  RESERVATION_EXPIRED(HttpStatus.BAD_REQUEST, "R006", "응답 기한이 지난 예약입니다."),

  // OAuth / Social Login
  UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "O001", "지원하지 않는 소셜 로그인 제공자입니다."),
  EMAIL_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "O002", "이메일 제공에 동의해야 로그인/회원가입이 가능합니다."),
  SOCIAL_TOKEN_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "O003", "소셜 토큰 발급에 실패했습니다."),
  SOCIAL_USERINFO_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "O004", "소셜 사용자 정보 조회에 실패했습니다."),
  INVALID_OAUTH_STATE(HttpStatus.BAD_REQUEST, "O005", "유효하지 않은 로그인 요청입니다(state 검증 실패)."),
  EMAIL_ALREADY_REGISTERED(
      HttpStatus.CONFLICT, "O006", "이미 다른 소셜 계정으로 가입된 이메일입니다. 기존 로그인 방식을 이용해 주세요."),

  // User
  INVALID_EMAIL(HttpStatus.BAD_REQUEST, "U001", "유효하지 않은 이메일입니다."),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U002", "회원 정보를 찾을 수 없습니다."),
  USER_ALREADY_BLACKLISTED(HttpStatus.CONFLICT, "U003", "이미 제재된 회원입니다."),
  USER_NOT_BLACKLISTED(HttpStatus.CONFLICT, "U004", "제재 상태가 아닌 회원입니다."),

  // Camp
  CAMP_NOT_FOUND(HttpStatus.NOT_FOUND, "CP001", "캠핑장을 찾을 수 없습니다."),
  ALREADY_DELETED_CAMP(HttpStatus.CONFLICT, "CP002", "이미 삭제된 캠핑장입니다."),
  CAMP_NOT_ACCESSED(HttpStatus.FORBIDDEN, "CP003", "접근 권한이 없습니다"),
  GOCAMPING_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CP004", " 고캠핑 서버 내부 오류가 발생했습니다."),
  INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "CP005", "페이지당 조회 개수는 1 이상이어야 합니다."),
  WISHLIST_ALREADY_EXISTS(HttpStatus.CONFLICT, "CP006", "이미 찜한 캠핑장입니다."),

  // CampOwner (캠핑업체 권한 승격 신청)
  CAMP_OWNER_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CO001", "업체 전환 신청을 찾을 수 없습니다."),
  CAMP_OWNER_APPLICATION_ALREADY_PENDING(HttpStatus.CONFLICT, "CO002", "이미 심사 중인 신청이 있습니다."),
  ALREADY_CAMP_OWNER(HttpStatus.CONFLICT, "CO003", "이미 캠핑업체로 등록된 회원입니다."),
  CAMP_OWNER_APPLICATION_ALREADY_PROCESSED(HttpStatus.CONFLICT, "CO004", "이미 처리된 신청입니다."),
  BUSINESS_NUMBER_ALREADY_APPROVED(HttpStatus.CONFLICT, "CO005", "이미 승인된 사업자등록번호입니다."),

  // Payment
  PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "결제 정보를 찾을 수 없습니다."),
  RESERVATION_NOT_PENDING_PAYMENT(HttpStatus.BAD_REQUEST, "P002", "결제 대기 상태의 예약만 결제할 수 있습니다."),
  ALREADY_PAID(HttpStatus.CONFLICT, "P003", "이미 결제가 완료된 예약입니다."),
  PAYMENT_NOT_REFUNDABLE(HttpStatus.NOT_FOUND, "P004", "결제 내역이 존재하지 않아 환불할 수 없습니다."),
  PAYMENT_ALREADY_REFUNDED(HttpStatus.CONFLICT, "P005", "이미 환불된 결제입니다."),
  // 포트원(PG) 연동
  PG_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "P006", "결제 연동 설정이 완료되지 않았습니다."),
  PG_COMMUNICATION_FAILED(HttpStatus.BAD_GATEWAY, "P007", "결제 서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요."),
  PG_PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "P008", "결제 서버에서 해당 결제 건을 찾을 수 없습니다."),
  // 결제창에 표시된 금액과 실제 승인 금액이 다르면 위·변조를 의심해야 하므로 결제를 확정하지 않는다.
  PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "P009", "결제 금액이 예약 금액과 일치하지 않습니다."),
  PAYMENT_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "P010", "아직 결제가 완료되지 않았습니다."),
  PG_REFUND_FAILED(HttpStatus.BAD_GATEWAY, "P011", "결제 취소 요청에 실패했습니다."),
  WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "P012", "웹훅 서명 검증에 실패했습니다."),
  UNSUPPORTED_PAYMENT_METHOD(HttpStatus.BAD_REQUEST, "P013", "유효하지 않는 결제 방식입니다."),

  // Post (게시글) — Payment가 P001~P004를 이미 쓰고 있어 접두어를 PO로 분리한다.
  POST_NOT_FOUND(HttpStatus.NOT_FOUND, "PO001", "게시글을 찾을 수 없습니다."),
  POST_BLINDED(HttpStatus.FORBIDDEN, "PO002", "관리자에 의해 블라인드 처리된 게시글입니다."),
  ALREADY_REPORTED_POST(HttpStatus.CONFLICT, "PO003", "이미 신고한 게시글입니다."),
  POST_ALREADY_BLINDED(HttpStatus.CONFLICT, "PO004", "이미 블라인드 처리된 게시글입니다."),
  REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "PO005", "신고 내역을 찾을 수 없습니다."),
  REPORT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "PO006", "이미 처리된 신고입니다."),

  // Comment (댓글)
  COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CM001", "댓글을 찾을 수 없습니다."),

  // Review (리뷰)
  REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "RV001", "리뷰를 찾을 수 없습니다."),
  REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "RV002", "이미 작성한 리뷰가 있습니다."),
  REVIEW_NOT_ALLOWED_BEFORE_CHECKOUT(
      HttpStatus.BAD_REQUEST, "RV003", "체크아웃이 완료된 예약만 리뷰를 작성할 수 있습니다."),

  // Image (이미지 업로드)
  INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "IMG001", "지원하지 않는 이미지 형식입니다."),
  IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "IMG002", "이미지 저장에 실패했습니다."),
  IMAGE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "IMG003", "첨부할 수 있는 이미지 개수를 초과했습니다."),
  IMAGE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "IMG004", "이미지 용량이 허용 범위를 초과했습니다."),

  // Notification (알림)
  NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없습니다."),
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
