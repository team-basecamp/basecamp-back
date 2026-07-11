package com.basecamp.backend.domain.payment.repository;

import com.basecamp.backend.domain.payment.entity.Payment;
import com.basecamp.backend.domain.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByReservationId(Long reservationId);

    Optional<Payment> findByReservationId(Long reservationId);

    @Modifying(clearAutomatically = true)
    @Query("""
        update Payment p
        set p.status = :next, p.refundedAt = :now
        where p.reservation.id in :ids and p.status = :current
        """)
    int bulkRefund(@Param("ids") List<Long> ids,
                   @Param("current") PaymentStatus current,
                   @Param("next") PaymentStatus next,
                   @Param("now") LocalDateTime now);
}
