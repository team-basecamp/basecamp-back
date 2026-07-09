package com.basecamp.backend.domain.reservation.controller;

import com.basecamp.backend.domain.reservation.dto.request.ReservationCreateRequest;
import com.basecamp.backend.domain.reservation.dto.response.CustomerReservationResponse;
import com.basecamp.backend.domain.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
    public ResponseEntity<CustomerReservationResponse> cancelReservation(@PathVariable Long reservationId){
        CustomerReservationResponse response = reservationService.cancelReservation(reservationId);

        return ResponseEntity.ok(response);
    }

    // TODO: 인증 미구현으로인한 하드코딩, Authentication authentication 나중에 넣을 파라미터
    @GetMapping("/me")
    public ResponseEntity<Page<CustomerReservationResponse>> findMyReservation(
            @ParameterObject
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ){
        //Long userId = Long.parseLong(authentication.getName()); // service에 전달할 파라미터
        return ResponseEntity.ok(reservationService.findAllReservations(1l, pageable));
    }
}
