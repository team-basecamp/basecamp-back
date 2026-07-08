package com.basecamp.backend.domain.reservation.controller;

import com.basecamp.backend.domain.reservation.dto.request.ReservationCreateRequest;
import com.basecamp.backend.domain.reservation.dto.response.CustomerReservationResponse;
import com.basecamp.backend.domain.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<CustomerReservationResponse> createReservation(@Valid @RequestBody ReservationCreateRequest request) {
        CustomerReservationResponse response = reservationService.createReservation(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long reservationId){
        reservationService.cancelReservation(reservationId);

        return ResponseEntity.noContent().build();
    }
}
