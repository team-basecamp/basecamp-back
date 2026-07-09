package com.basecamp.backend.domain.reservation.repository;

import com.basecamp.backend.domain.reservation.entity.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository extends JpaRepository <Reservation, Long> {
    // userId를 조건으로 그 유저의 모든 예약 조회, 정렬 조건은 최신순으로
    Page<Reservation> findAllByUserId(Long userId, Pageable pageable);
}
