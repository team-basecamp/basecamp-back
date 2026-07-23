package com.basecamp.backend.domain.review.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.review.dto.request.ReviewRequest;
import com.basecamp.backend.domain.review.dto.request.ReviewUpdateRequest;
import com.basecamp.backend.domain.review.dto.response.ReviewResponse;
import com.basecamp.backend.domain.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ReviewController {
  // 리뷰 비즈니스 로직 위임 대상
  private final ReviewService reviewService;

  // 리뷰 작성: 인증 회원이 본인의 예약(reservationId)에 리뷰를 등록하고 생성된 리뷰를 반환한다.
  @Operation(
      summary = "리뷰 작성",
      description =
          "예약자 본인이 체크아웃을 마친 예약에 대해 리뷰를 작성한다. multipart/form-data 로 전송하며, "
              + "'request'(application/json) 파트에 평점·내용을, 'images' 파트에 이미지 파일들을 담는다. "
              + "이미지는 선택이며 로컬에 저장되고 응답의 imageUrls 에 상대경로(/images/파일명)로 내려간다.")
  // Swagger UI 가 'request' 파트를 text/plain 으로 보내 415 가 나는 것을 막는다.
  // @Encoding 으로 해당 파트의 Content-Type 을 application/json 으로 명시한다. (문서/Swagger 전송 형식에만 영향, 런타임 계약은
  // 그대로)
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
              encoding =
                  @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE)))
  @PostMapping(
      value = "/api/v1/reservations/{reservationId}/reviews",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ReviewResponse> createReview(
      @AuthenticationPrincipal AuthUser user, // JWT에서 꺼낸 로그인 회원 (id, role)
      @PathVariable Long reservationId, // 리뷰를 남길 예약 id (경로 변수)
      @RequestPart("request") @Valid ReviewRequest request, // 작성 요청 본문(JSON 파트, 검증 대상)
      @RequestPart(value = "images", required = false) List<MultipartFile> images) { // 첨부 이미지(선택)

    ReviewResponse response = reviewService.createReview(user.id(), reservationId, request, images);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  // 리뷰 수정: 인증 회원이 본인이 쓴 리뷰(reviewId)의 평점·본문과 첨부 이미지를 수정하고 수정된 리뷰를 반환한다.
  @Operation(
      summary = "리뷰 수정",
      description =
          "예약자 본인이 작성한 리뷰의 평점·내용과 첨부 이미지를 수정한다. "
              + "multipart/form-data 로 전송하며, 'request'(application/json) 파트에 본문 필드를, "
              + "'images' 파트에 새로 추가할 이미지 파일들을 담는다. "
              + "이미지는 전체 교체 방식이라 최종 첨부 = request.keepImageUrls(남길 기존 이미지) + images(새 파일) 이며, "
              + "keepImageUrls 에 넣지 않은 기존 이미지는 삭제된다. "
              + "keepImageUrls 를 생략하면 기존 이미지를 그대로 유지하고, 빈 배열([])을 보내면 전부 삭제한다.")
  // 작성 API와 같은 이유 — Swagger UI 가 'request' 파트를 text/plain 으로 보내 415 가 나는 것을 막는다.
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
              encoding =
                  @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE)))
  @PostMapping(value = "/api/v1/reviews/{reviewId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ReviewResponse> updateReview(
      @AuthenticationPrincipal AuthUser user, // JWT에서 꺼낸 로그인 회원 (id, role)
      @PathVariable Long reviewId, // 수정할 리뷰 id (경로 변수)
      @RequestPart("request") @Valid ReviewUpdateRequest request, // 수정 요청 본문(JSON 파트, 검증 대상)
      @RequestPart(value = "images", required = false)
          List<MultipartFile> images) { // 새로 추가할 이미지(선택)

    ReviewResponse response = reviewService.updateReview(user.id(), reviewId, request, images);

    return ResponseEntity.ok(response);
  }

  // 리뷰 삭제: 인증 회원이 본인이 쓴 리뷰(reviewId)를 삭제한다. (하드 삭제)
  @Operation(
      summary = "리뷰 삭제",
      description = "예약자 본인이 작성한 리뷰를 삭제한다. " + "첨부 이미지도 images 행과 실제 파일까지 되돌릴 수 없게 함께 삭제된다.")
  @PostMapping("/api/v1/reviews/{reviewId}/delete")
  public ResponseEntity<Void> deleteReview(
      @AuthenticationPrincipal AuthUser user, // JWT에서 꺼낸 로그인 회원 (id, role)
      @PathVariable Long reviewId) { // 삭제할 리뷰 id (경로 변수)

    reviewService.deleteReview(user.id(), reviewId);

    return ResponseEntity.noContent().build();
  }

  // 리뷰 목록 조회: 경로의 캠핑장(campId)에 달린 리뷰를 최신순으로 반환한다.
  @Operation(summary = "캠핑장 리뷰 목록 조회", description = "캠핑장에 작성된 리뷰를 최신순으로 조회한다.")
  @GetMapping("/api/v1/camps/{campId}/reviews")
  public ResponseEntity<List<ReviewResponse>> getReviews(
      @PathVariable Long campId) { // 리뷰를 조회할 캠핑장 id (경로 변수)
    List<ReviewResponse> responses = reviewService.getReviewsByCamp(campId);

    return ResponseEntity.ok(responses);
  }

  // 내가 쓴 리뷰 목록: 로그인 회원 본인이 작성한 리뷰를 모아서 반환한다.
  @Operation(summary = "내 리뷰 목록 조회", description = "로그인한 회원이 작성한 리뷰를 최신순으로 조회한다.")
  @GetMapping("/api/v1/reviews/me")
  public ResponseEntity<List<ReviewResponse>> getMyReviews(@AuthenticationPrincipal AuthUser user) {
    // 회원 id는 토큰에서 꺼낸 user.id()만 신뢰한다.
    List<ReviewResponse> reviews = reviewService.getMyReviews(user.id());
    return ResponseEntity.ok(reviews);
  }
}
