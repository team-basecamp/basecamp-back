package com.basecamp.backend.domain.reservation.repository;

import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository <Reservation, Long> {
    // userId를 조건으로 그 유저의 모든 예약 조회, 정렬 조건은 최신순으로
    Page<Reservation> findAllByUserId(Long userId, Pageable pageable);

    // campId와 파라미터로 넣은 예약상태를 제외한 조건으로 해당 캠핑장의 모든 예약 조회
    Page<Reservation> findAllByCampIdAndStatusNot(Long campId, ReservationStatus status, Pageable pageable);

    // 중복예약 방지 쿼리(pending, reserved 상태에서 다시 예약 걸지 못하도록 막기)
    @Query("""
        select count(r) > 0 from Reservation r
        where r.userId = :userId
          and r.campId = :campId
          and (
               r.status in :activeStatuses
               or (r.status = :paymentWaiting and r.createdAt > :paymentValidAfter)
          )
          and r.checkInDate < :checkOutDate
          and r.checkOutDate > :checkInDate
        """)
    boolean existsOverbookingReservation(
            @Param("userId") Long userId,
            @Param("campId") Long campId,
            @Param("activeStatuses") List<ReservationStatus> activeStatuses,
            @Param("paymentWaiting") ReservationStatus paymentWaiting,
            @Param("paymentValidAfter") LocalDateTime paymentValidAfter,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate);

    // 예약날짜 충돌체크 쿼리
    @Query("""
        select count(r) > 0 from Reservation r
        where r.campId = :campId
          and r.id <> :excludeId
          and r.status = :status
          and r.checkInDate < :checkOutDate
          and r.checkOutDate > :checkInDate
        """)
    boolean existsConflictingReservation(
            @Param("campId") Long campId,
            @Param("excludeId") Long excludeId,
            @Param("status") ReservationStatus status,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate);
}
