package com.basecamp.backend.domain.auth.dto.response;

/**
 * 네이버 로그인 시작 시 서버가 발급하는 서명된 state 응답. 프론트는 이 값을 네이버 authorize 요청의 {@code state} 로 사용하고, 콜백에서 그대로
 * 백엔드로 되돌려준다.
 */
public record LoginStateResponse(String state) {}
