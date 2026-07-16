package com.basecamp.backend.domain.review.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.review.dto.request.ReviewCreateRequest;
import com.basecamp.backend.domain.review.dto.response.ReviewResponse;
import com.basecamp.backend.domain.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {
    // 리뷰 비즈니스 로직 위임 대상
    private final ReviewService reviewService;

    // 리뷰 작성: 인증 회원이 본인의 예약(reservationId)에 리뷰를 등록하고 생성된 리뷰를 반환한다.
    @Operation(summary = "리뷰 작성", description = "예약자 본인이 체크아웃을 마친 예약에 대해 리뷰를 작성한다.")
    @PostMapping("/api/v1/reservations/{reservationId}/reviews")
    public ResponseEntity<ReviewResponse> createReview(
            @AuthenticationPrincipal AuthUser user,                 // JWT에서 꺼낸 로그인 회원 (id, role)
            @PathVariable("reservationId") Long reservationId,      // 리뷰를 남길 예약 id (경로 변수)
            @RequestBody @Valid ReviewCreateRequest request) {      // 작성 요청 본문(검증 대상)

        ReviewResponse response = reviewService.createReview(user.id(), reservationId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


}
