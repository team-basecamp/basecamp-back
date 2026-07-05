package com.basecamp.backend.domain.reservation.repository;

import com.basecamp.backend.domain.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

}
