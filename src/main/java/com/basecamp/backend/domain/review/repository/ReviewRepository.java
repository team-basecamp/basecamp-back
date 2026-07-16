package com.basecamp.backend.domain.review.repository;

import com.basecamp.backend.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 예약당 리뷰는 한 건(reviews.reservation_id UNIQUE)이라, 작성 전 중복 여부를 이 조회로 막는다.
    // r.reservation.id는 reviews.reservation_id FK 컬럼을 그대로 읽으므로 reservations 조인이 붙지 않는다.
    boolean existsByReservation_Id(Long reservationId);

    // 한 캠핑장의 리뷰 목록을 최신순으로 조회한다.
    // 응답에 작성자 nickname을 담아야 하는데, 리뷰마다 예약·회원을 따로 로딩하면 N+1이 되므로
    // 예약(reservation)과 그 회원(user)을 fetch join으로 함께 로딩한다. (둘 다 NOT NULL이라 inner join)
    @Query("""
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
    @Query("""
            select rv from Review rv
            join fetch rv.reservation rs
            join fetch rs.user
            where rv.reviewId = :reviewId
            """)
    Optional<Review> findByIdWithReservation(@Param("reviewId") Long reviewId);
}
