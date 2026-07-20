package com.basecamp.backend.domain.review.dto.response;

import com.basecamp.backend.domain.review.entity.Review;
import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// 리뷰 응답 DTO(작성/목록 조회 공용). 엔티티를 직접 노출하지 않고 이 DTO로 변환해 반환한다.
// 어느 캠핑장·예약의 리뷰인지(campId, reservationId)와 함께, 화면에 바로 보여줄 작성자 정보(userId, nickname)를 담는다.
public record ReviewResponse(
    Long reviewId,
    Long campId,
    Long reservationId,
    Long userId,
    String nickname,
    BigDecimal rating,
    String content,
    List<String> imageUrls,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  // 엔티티 → DTO 변환 정적 팩토리.
  // review.getImages()도 LAZY라 같은 트랜잭션 안에서 초기화해 상대경로만 뽑아 담는다.
  // 목록 조회처럼 리뷰가 여러 건인 경로에서는 Review.images 의 @BatchSize 가 IN 조회로 묶어 채운다.
  public static ReviewResponse from(Review review) {
    User user = review.getReservation().getUser();
    List<String> imageUrls = review.getImages().stream().map(Image::getImageUrl).toList();
    return new ReviewResponse(
        review.getReviewId(),
        review.getCamp().getCampId(),
        review.getReservation().getId(),
        user.getId(),
        user.getNickname(),
        review.getRating(),
        review.getContent(),
        imageUrls,
        review.getCreatedAt(),
        review.getUpdatedAt());
  }
}
