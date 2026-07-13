package com.basecamp.backend.domain.payment.entity;

public enum PaymentMethod {
    CARD,             // 카드(신용,체크) 결제
    KAKAO_PAY,        // 카카오 페이
    NAVER_PAY,        // 네이버 페이
    TOSS_PAY          // 토스 페이
}
