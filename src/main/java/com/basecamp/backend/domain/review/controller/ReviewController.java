package com.basecamp.backend.domain.review.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.review.dto.request.ReviewRequest;
import com.basecamp.backend.domain.review.dto.response.ReviewResponse;
import com.basecamp.backend.domain.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @PathVariable Long reservationId,                       // 리뷰를 남길 예약 id (경로 변수)
            @RequestBody @Valid ReviewRequest request) {            // 작성 요청 본문(검증 대상)

        ReviewResponse response = reviewService.createReview(user.id(), reservationId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 리뷰 수정: 인증 회원이 본인이 쓴 리뷰(reviewId)의 평점·본문을 수정하고 수정된 리뷰를 반환한다.
    @Operation(summary = "리뷰 수정", description = "예약자 본인이 작성한 리뷰의 평점·내용을 수정한다.")
    @PostMapping("/api/v1/reviews/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(
            @AuthenticationPrincipal AuthUser user,                 // JWT에서 꺼낸 로그인 회원 (id, role)
            @PathVariable Long reviewId,                            // 수정할 리뷰 id (경로 변수)
            @RequestBody @Valid ReviewRequest request) {            // 수정 요청 본문(검증 대상)

        ReviewResponse response = reviewService.updateReview(user.id(), reviewId, request);

        return ResponseEntity.ok(response);
    }

    // 리뷰 삭제: 인증 회원이 본인이 쓴 리뷰(reviewId)를 삭제한다. (하드 삭제)
    @Operation(summary = "리뷰 삭제", description = "예약자 본인이 작성한 리뷰를 삭제한다.")
    @PostMapping("/api/v1/reviews/{reviewId}/delete")
    public ResponseEntity<Void> deleteReview(
            @AuthenticationPrincipal AuthUser user,                 // JWT에서 꺼낸 로그인 회원 (id, role)
            @PathVariable Long reviewId) {                          // 삭제할 리뷰 id (경로 변수)

        reviewService.deleteReview(user.id(), reviewId);

        return ResponseEntity.noContent().build();
    }

    // 리뷰 목록 조회: 경로의 캠핑장(campId)에 달린 리뷰를 최신순으로 반환한다.
    @Operation(summary = "캠핑장 리뷰 목록 조회", description = "캠핑장에 작성된 리뷰를 최신순으로 조회한다.")
    @GetMapping("/api/v1/camps/{campId}/reviews")
    public ResponseEntity<List<ReviewResponse>> getReviews(
            @PathVariable Long campId) {                        // 리뷰를 조회할 캠핑장 id (경로 변수)
        List<ReviewResponse> responses = reviewService.getReviewsByCamp(campId);

        return ResponseEntity.ok(responses);
    }

    // 내가 쓴 리뷰 목록: 로그인 회원 본인이 작성한 리뷰를 모아서 반환한다.
    @Operation(summary = "내 리뷰 목록 조회", description = "로그인한 회원이 작성한 리뷰를 최신순으로 조회한다.")
    @GetMapping("/api/v1/reviews/me")
    public ResponseEntity<List<ReviewResponse>> getMyReviews(
            @AuthenticationPrincipal AuthUser user) {
        // 회원 id는 토큰에서 꺼낸 user.id()만 신뢰한다.
        List<ReviewResponse> reviews = reviewService.getMyReviews(user.id());
        return ResponseEntity.ok(reviews);
    }

}
