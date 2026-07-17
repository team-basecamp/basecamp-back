package com.basecamp.backend.domain.review.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.basecamp.backend.domain.reservation.repository.ReservationRepository;
import com.basecamp.backend.domain.review.dto.request.ReviewRequest;
import com.basecamp.backend.domain.review.dto.response.ReviewResponse;
import com.basecamp.backend.domain.review.entity.Review;
import com.basecamp.backend.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// 리뷰 비즈니스 로직. 기본은 읽기 전용 트랜잭션, 쓰기 메서드에만 @Transactional을 따로 건다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final CampRepository campRepository;

    // 리뷰 작성: 예약 소유자가 체크아웃을 마친 예약에 한해 리뷰를 남길 수 있다. (쓰기 트랜잭션)
    @Transactional
    public ReviewResponse createReview(Long userId, Long reservationId, ReviewRequest request) {
        // 리뷰를 달 예약을 먼저 조회한다.
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        // 예약 소유자(예약자) 본인만 리뷰를 작성할 수 있다.
        if (!reservation.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 체크아웃이 완료된(확정 + 체크아웃 날짜 경과) 예약만 리뷰를 쓸 수 있다.
        validateCheckedOut(reservation);

        // 예약당 리뷰는 한 건(reviews.reservation_id UNIQUE). 이미 있으면 409.
        if (reviewRepository.existsByReservation_Id(reservationId)) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        // 리뷰 대상 캠핑장은 예약의 캠핑장을 그대로 따른다. (별도 입력 없이 예약에서 파생)
        Review review = Review.builder()
                .reservation(reservation)
                .camp(reservation.getCamp())
                .rating(request.rating())
                .content(request.content())
                .build();

        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        refreshCampAverageRating(review.getCamp().getCampId());
        return ReviewResponse.from(review);
    }

    // 리뷰 수정: 리뷰를 조회해 예약 소유자 본인일 때만 평점·본문을 갈아끼운다. (쓰기 트랜잭션)
    @Transactional
    public ReviewResponse updateReview(Long userId, Long reviewId, ReviewRequest request) {
        // 수정할 리뷰를 예약·작성자와 함께 조회한다. 없으면 404.
        Review review = reviewRepository.findByIdWithReservation(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        // 예약 소유자(작성자) 본인만 수정할 수 있다. 아니면 403.
        if (!review.getReservation().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 관리 상태 엔티티라 update 후 트랜잭션 커밋 시 변경 감지로 UPDATE가 나간다.
        review.update(request.rating(), request.content());

        refreshCampAverageRating(review.getCamp().getCampId());
        return ReviewResponse.from(review);
    }

    // 리뷰 삭제: 리뷰를 조회해 예약 소유자 본인일 때만 실제 행을 삭제한다. (하드 삭제)
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        // 삭제할 리뷰를 예약·작성자와 함께 조회한다. 없으면 404.
        Review review = reviewRepository.findByIdWithReservation(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        // 예약 소유자(작성자) 본인만 삭제할 수 있다. 아니면 403.
        if (!review.getReservation().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 캠핑장 식별자는 delete 전에 잡아둔다. (삭제 후엔 review에서 꺼내 쓰지 않는다)
        Long campId = review.getCamp().getCampId();
        reviewRepository.delete(review);

        refreshCampAverageRating(campId);
    }

    // 캠핑장 리뷰 목록 조회: 해당 캠핑장의 리뷰를 최신순으로 반환한다. (읽기 전용 트랜잭션)
    public List<ReviewResponse> getReviewsByCamp(Long campId) {
        return reviewRepository.findByCampIdWithUser(campId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    // 내가 쓴 리뷰 목록 조회: 로그인한 회원이 작성한 리뷰를 최신순으로 반환한다. (읽기 전용 트랜잭션)
    public List<ReviewResponse> getMyReviews(Long userId) {
        return reviewRepository.findByReservationUserIdWithDetails(userId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    // camps.average_rating 재계산: 리뷰가 바뀔 때마다 해당 캠핑장의 평균을 다시 집계해 캐싱 컬럼에 반영한다.
    // 집계·반영을 UPDATE 한 문장으로 처리해 같은 캠핑장에 대한 동시 갱신을 직렬화한다. (CampRepository 주석 참고)
    // 방금의 수정(변경 감지)·삭제는 아직 DB에 반영되지 않은 상태라, flushAutomatically로 UPDATE 전에 밀어넣는다.
    // campId는 프록시를 초기화하지 않고 읽히므로 삭제된 캠핑장의 리뷰를 건드려도 프록시 초기화로 터지지 않는다.
    private void refreshCampAverageRating(Long campId) {
        campRepository.refreshAverageRating(campId);
    }

    // 체크아웃 완료 검증: 예약이 확정(RESERVED) 상태이고 체크아웃 날짜가 지났을 때만 리뷰 작성을 허용한다.
    private void validateCheckedOut(Reservation reservation) {
        boolean checkedOut = reservation.getStatus() == ReservationStatus.RESERVED
                && reservation.getCheckOutDate().isBefore(LocalDate.now());
        if (!checkedOut) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED_BEFORE_CHECKOUT);
        }
    }
}
