package com.basecamp.backend.domain.review.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.basecamp.backend.domain.reservation.repository.ReservationRepository;
import com.basecamp.backend.domain.review.dto.request.ReviewCreateRequest;
import com.basecamp.backend.domain.review.dto.response.ReviewResponse;
import com.basecamp.backend.domain.review.entity.Review;
import com.basecamp.backend.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// 리뷰 비즈니스 로직. 기본은 읽기 전용 트랜잭션, 쓰기 메서드에만 @Transactional을 따로 건다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    // 리뷰 저장/조회 리포지토리
    private final ReviewRepository reviewRepository;
    // 대상 예약 조회 리포지토리 (소유권·체크아웃 검증용)
    private final ReservationRepository reservationRepository;

    // 리뷰 작성: 예약 소유자가 체크아웃을 마친 예약에 한해 리뷰를 남길 수 있다. (쓰기 트랜잭션)
    // userId는 컨트롤러에서 토큰(AuthUser)으로부터 넘어온 값이라 신뢰할 수 있다.
    @Transactional
    public ReviewResponse createReview(Long userId, Long reservationId, ReviewCreateRequest request) {
        // 리뷰를 달 예약을 먼저 조회한다. 없으면 404.
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        // 예약 소유자(예약자) 본인만 리뷰를 작성할 수 있다. 남의 예약이면 403.
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
        Review review = new Review(reservation, reservation.getCamp(), request.rating(), request.content());
        Review saved = reviewRepository.save(review);
        // reservation·user가 이미 로딩된 상태라 from()에서 nickname 접근 시 추가 쿼리가 없다.
        return ReviewResponse.from(saved);
    }



    // 체크아웃 완료 검증: 예약이 확정(RESERVED) 상태이고 체크아웃 날짜가 지났을 때만 리뷰 작성을 허용한다.
    // 아직 체크아웃 전이거나 취소/거절된 예약이면 400.
    private void validateCheckedOut(Reservation reservation) {
        boolean checkedOut = reservation.getStatus() == ReservationStatus.RESERVED
                && !reservation.getCheckOutDate().isAfter(LocalDate.now());
        if (!checkedOut) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED_BEFORE_CHECKOUT);
        }
    }
}
