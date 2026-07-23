package com.basecamp.backend.domain.reservation.repository;

import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
  // userId를 조건으로 그 유저의 모든 예약 조회, 정렬 조건은 최신순으로
  @EntityGraph(attributePaths = "camp")
  Page<Reservation> findAllByUserId(Long userId, Pageable pageable);

  // campId와 파라미터로 넣은 예약상태를 제외한 조건으로 해당 캠핑장의 모든 예약 조회
  Page<Reservation> findAllByCamp_CampIdAndStatusNot(
      Long campId, ReservationStatus status, Pageable pageable);

  // 캠핑장 삭제 시 진행 중인 예약을 일괄 취소하기 위한 조회 (user/camp 를 함께 fetch 해서 취소·환불·알림 처리 중 지연 로딩 예외가 나지 않게 한다)
  @Query(
      """
        select r from Reservation r
        join fetch r.user
        join fetch r.camp
        where r.camp.campId = :campId
          and r.status in :statuses
        """)
  List<Reservation> findAllByCamp_CampIdAndStatusIn(
      @Param("campId") Long campId, @Param("statuses") List<ReservationStatus> statuses);

  // 중복예약 방지 쿼리(pending, reserved 상태에서 다시 예약 걸지 못하도록 막기)
  @Query(
      """
        select count(r) > 0 from Reservation r
        where r.user.id = :userId
          and r.camp.campId = :campId
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

  @Modifying(clearAutomatically = true)
  @Query(
      """
        update Reservation r
        set r.status = :expiredStatus, r.version = r.version + 1
        where r.user.id = :userId
          and r.camp.campId = :campId
          and r.status = :paymentWaiting
          and r.createdAt <= :paymentValidAfter
        """)
  int expireStalePaymentWaiting(
      @Param("userId") Long userId,
      @Param("campId") Long campId,
      @Param("paymentWaiting") ReservationStatus paymentWaiting,
      @Param("expiredStatus") ReservationStatus expiredStatus,
      @Param("paymentValidAfter") LocalDateTime paymentValidAfter);

  // 예약날짜 충돌체크 쿼리
  @Query(
      """
        select count(r) > 0 from Reservation r
        where r.camp.campId = :campId
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

  @Query(
      "select r.id from Reservation r "
          + "where r.status = :status and r.expiredAt < :now "
          + "order by r.expiredAt asc")
  List<Long> findExpiredPendingIds(
      @Param("status") ReservationStatus status,
      @Param("now") LocalDateTime now,
      Pageable pageable);

  // 체크인 D-1 알림 대상: 주어진 날짜에 체크인하는 확정 예약. user/camp 를 함께 fetch 해
  // 스케줄러가 트랜잭션 밖에서 알림 메시지를 만들 때 지연 로딩 예외가 나지 않게 한다.
  @Query(
      """
        select r from Reservation r
        join fetch r.user
        join fetch r.camp
        where r.checkInDate = :checkInDate and r.status = :status
        """)
  List<Reservation> findAllForCheckInReminder(
      @Param("checkInDate") LocalDate checkInDate, @Param("status") ReservationStatus status);

  // 비관적 락을 통한 동일예약에 대한 동시 결제 요청을 직렬화
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Reservation r where r.id = :id")
  Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

  // 사업자 대시보드 통계: 기간 내 상태별 매출 합계 (반개구간 [from, to))
  @Query(
      """
        select coalesce(sum(r.totalPrice), 0) from Reservation r
        where r.camp.ownerId = :ownerId
          and r.status in :statuses
          and r.createdAt >= :from and r.createdAt < :to
        """)
  long sumRevenueByOwnerAndPeriod(
      @Param("ownerId") Long ownerId,
      @Param("statuses") List<ReservationStatus> statuses,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  // 사업자 대시보드 통계: 기간 내 상태별 예약 건수 (반개구간 [from, to))
  @Query(
      """
        select count(r) from Reservation r
        where r.camp.ownerId = :ownerId
          and r.status in :statuses
          and r.createdAt >= :from and r.createdAt < :to
        """)
  long countByOwnerAndPeriod(
      @Param("ownerId") Long ownerId,
      @Param("statuses") List<ReservationStatus> statuses,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  // 사업자 대시보드 통계: 기간 조건 없는 상태별 예약 건수 (승인 대기 배지용).
  // 매출·건수와 달리 "지금 처리해야 할 건"이라 이번달/올해로 자르지 않는다.
  @Query(
      """
        select count(r) from Reservation r
        where r.camp.ownerId = :ownerId
          and r.status in :statuses
        """)
  long countByOwnerAndStatuses(
      @Param("ownerId") Long ownerId, @Param("statuses") List<ReservationStatus> statuses);

  // 사업자 대시보드 통계: 월별 매출, 예약 건수
  @Query(
      """
        select month(r.createdAt) as month,
               coalesce(sum(r.totalPrice), 0) as revenue,
               count(r) as count
        from Reservation r
        where r.camp.ownerId = :ownerId
          and r.status = :status
          and r.createdAt >= :from and r.createdAt < :to
        group by month(r.createdAt)
        order by month(r.createdAt)
        """)
  List<MonthlyRevenueProjection> findMonthlyRevenueByOwner(
      @Param("ownerId") Long ownerId,
      @Param("status") ReservationStatus status,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
