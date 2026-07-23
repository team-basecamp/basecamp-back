package com.basecamp.backend.domain.payment.entity;

/**
 * 우리 서비스가 취급하는 결제 수단. 포트원 결제 완료 응답의 {@code method.type}(+ 간편결제 {@code provider})을 이 값으로 옮겨 담으며, 매핑은
 * {@code PortOneMethodMapper} 가 담당한다.
 *
 * <p>여기 없는 수단(계좌이체·가상계좌·휴대폰결제 등)은 결제창에 노출하지 않으므로 정상 흐름에서는 들어오지 않는다. 그래도 들어온 경우 매퍼가 {@code null} 로
 * 남기고 경고 로그만 남긴다 — 자세한 이유는 매퍼 주석 참고.
 */
public enum PaymentMethod {
  CARD, // 카드(신용/체크) 결제
  KAKAO_PAY, // 카카오페이
  // NAVER_PAY,         // 네이버페이 : 테스트 인증 키를 받기에 절차가 너무 까다로워서 미구현
  TOSS_PAY, // 토스페이
}
