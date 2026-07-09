package com.basecamp.backend.domain.reservation.controller;

import com.basecamp.backend.domain.reservation.dto.request.ReservationCreateRequest;
import com.basecamp.backend.domain.reservation.dto.request.ReservationRejectRequest;
import com.basecamp.backend.domain.reservation.dto.response.ReservationResponse;
import com.basecamp.backend.domain.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
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

    // TODO: 인증 미구현으로인한 하드코딩, Authentication authentication 나중에 넣을 파라미터
    @Operation(summary = "예약하기", description = "고객이 해당 캠핑장에 예약을 합니다. 이 때 예약상태는 대기(PENDING)입니다.")
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody ReservationCreateRequest request) {
        ReservationResponse response = reservationService.createReservation(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "예약 취소", description = "대기(PENDING) 상태의 예약을 고객이 취소합니다.")
    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(@PathVariable Long reservationId){
        ReservationResponse response = reservationService.cancelReservation(reservationId);

        return ResponseEntity.ok(response);
    }

    // TODO: 업체 인증/인가 미구현으로 인한 하드코딩, 캠핑장 소유자 검증 나중에 추가
    @Operation(summary = "예약 수락", description = "대기(PENDING) 상태의 예약을 업체가 수락합니다.")
    @PostMapping("/{reservationId}/approve")
    public ResponseEntity<ReservationResponse> approveReservation(@PathVariable Long reservationId){
        ReservationResponse response = reservationService.approveReservation(reservationId);

        return ResponseEntity.ok(response);
    }

    // TODO: 업체 인증/인가 미구현으로 인한 하드코딩, 캠핑장 소유자 검증 나중에 추가
    @Operation(summary = "예약 거절", description = "대기(PENDING) 상태의 예약을 업체가 사유와 함께 거절합니다.")
    @PostMapping("/{reservationId}/reject")
    public ResponseEntity<ReservationResponse> rejectReservation(
            @PathVariable Long reservationId,
            @Valid @RequestBody ReservationRejectRequest request
    ){
        ReservationResponse response = reservationService.rejectReservation(reservationId, request);

        return ResponseEntity.ok(response);
    }

    // TODO: 인증 미구현으로인한 하드코딩, Authentication authentication 나중에 넣을 파라미터
    @Operation(summary = "내 예약 목록 조회", description = "로그인한 사용자의 예약 목록을 페이지네이션으로 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<Page<ReservationResponse>> findMyReservation(
            @ParameterObject
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ){
        //Long userId = Long.parseLong(authentication.getName()); // service에 전달할 파라미터
        return ResponseEntity.ok(reservationService.findAllReservations(1l, pageable));
    }

    @Operation(summary = "캠핑장 업체별 예약 목록 조회", description = "특정 캠핑장의 예약 목록을 페이지네이션으로 조회합니다.(CANCELLED 상태 제외)")
    @GetMapping("/camps/{campId}")
    public ResponseEntity<Page<ReservationResponse>> findReservationsByCamp(
            @PathVariable Long campId,
            @ParameterObject
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ){
        return ResponseEntity.ok(reservationService.findAllReservationsByCamp(campId, pageable));
    }
}
