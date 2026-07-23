package com.basecamp.backend.domain.review.repository;

import com.basecamp.backend.domain.review.entity.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

  // 예약당 리뷰는 한 건(reviews.reservation_id UNIQUE)이라, 작성 전 중복 여부를 이 조회로 막는다.
  // r.reservation.id는 reviews.reservation_id FK 컬럼을 그대로 읽으므로 reservations 조인이 붙지 않는다.
  boolean existsByReservation_Id(Long reservationId);

  // 한 캠핑장의 리뷰 목록을 최신순으로 조회한다.
  // 예약(reservation)과 그 회원(user)을 fetch join으로 함께 로딩한다. (둘 다 NOT NULL이라 inner join)
  @Query(
      """
            select rv from Review rv
            join fetch rv.reservation rs
            join fetch rs.user
            where rv.camp.campId = :campId
            order by rv.createdAt desc, rv.reviewId desc
            """)
  List<Review> findByCampIdWithUser(@Param("campId") Long campId);

  // 단건 리뷰를 예약·작성자와 함께 조회한다. (수정/삭제 시 소유권 검증과 응답 변환을 추가 쿼리 없이 처리)
  //   - join fetch rv.reservation : 소유권 검증(예약자 == 요청자)에 쓰므로 함께 로딩
  //   - join fetch rs.user        : 응답의 작성자 nickname 로딩
  @Query(
      """
            select rv from Review rv
            join fetch rv.reservation rs
            join fetch rs.user
            where rv.reviewId = :reviewId
            """)
  Optional<Review> findByIdWithReservation(@Param("reviewId") Long reviewId);

  @Query(
      """
        SELECT r FROM Review r
        JOIN FETCH r.reservation res
        JOIN FETCH res.user
        JOIN FETCH r.camp
        WHERE res.user.id = :userId
        ORDER BY r.createdAt DESC, r.reviewId DESC
        """)
  List<Review> findByReservationUserIdWithDetails(@Param("userId") Long userId);

  // 예약 목록에 hasReview를 채우기 위해, 주어진 예약 id들 중 이미 리뷰가 달린 것만 골라낸다.
  @Query("select rv.reservation.id from Review rv where rv.reservation.id in :reservationIds")
  List<Long> findReservationIdsWithReview(@Param("reservationIds") List<Long> reservationIds);
}
