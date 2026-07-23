package com.basecamp.backend.domain.reservation.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.reservation.dto.request.ReservationCreateRequest;
import com.basecamp.backend.domain.reservation.dto.request.ReservationRejectRequest;
import com.basecamp.backend.domain.reservation.dto.response.MonthlyRevenueResponse;
import com.basecamp.backend.domain.reservation.dto.response.ReservationListResponse;
import com.basecamp.backend.domain.reservation.dto.response.ReservationResponse;
import com.basecamp.backend.domain.reservation.dto.response.ReservationStatsResponse;
import com.basecamp.backend.domain.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

  private final ReservationService reservationService;

  @Operation(
      summary = "예약하기",
      description = "고객이 해당 캠핑장에 예약을 합니다. 이 때 예약상태는 결제 대기(PENDING_PAYMENT)입니다.")
  @PostMapping
  public ResponseEntity<ReservationResponse> createReservation(
      @AuthenticationPrincipal AuthUser user,
      @Valid @RequestBody ReservationCreateRequest request) {
    ReservationResponse response = reservationService.createReservation(request, user.id());

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(summary = "예약 취소", description = "대기(PENDING) 상태의 예약을 고객이 취소합니다.")
  @PostMapping("/{reservationId}/cancel")
  public ResponseEntity<ReservationResponse> cancelReservation(
      @PathVariable Long reservationId, @AuthenticationPrincipal AuthUser user) {
    ReservationResponse response = reservationService.cancelReservation(reservationId, user.id());

    return ResponseEntity.ok(response);
  }

  @Operation(summary = "예약 수락", description = "대기(PENDING) 상태의 예약을 업체가 수락합니다.")
  @PreAuthorize("hasRole('CAMP_OWNER')")
  @PostMapping("/{reservationId}/approve")
  public ResponseEntity<ReservationResponse> approveReservation(
      @PathVariable Long reservationId, @AuthenticationPrincipal AuthUser user) {
    ReservationResponse response = reservationService.approveReservation(reservationId, user.id());

    return ResponseEntity.ok(response);
  }

  @Operation(summary = "예약 거절", description = "대기(PENDING) 상태의 예약을 업체가 사유와 함께 거절합니다.")
  @PreAuthorize("hasRole('CAMP_OWNER')")
  @PostMapping("/{reservationId}/reject")
  public ResponseEntity<ReservationResponse> rejectReservation(
      @PathVariable Long reservationId,
      @Valid @RequestBody ReservationRejectRequest request,
      @AuthenticationPrincipal AuthUser user) {
    ReservationResponse response =
        reservationService.rejectReservation(reservationId, request, user.id());

    return ResponseEntity.ok(response);
  }

  @Operation(summary = "내 예약 목록 조회", description = "로그인한 사용자의 예약 목록을 페이지네이션으로 조회합니다.")
  @GetMapping("/me")
  public ResponseEntity<Page<ReservationListResponse>> findMyReservation(
      @ParameterObject
          @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.ok(reservationService.findAllReservations(user.id(), pageable));
  }

  @Operation(
      summary = "캠핑장 업체별 예약 목록 조회",
      description = "특정 캠핑장의 예약 목록을 페이지네이션으로 조회합니다.(CANCELLED 상태 제외)")
  @PreAuthorize("hasRole('CAMP_OWNER')")
  @GetMapping("/camps/{campId}")
  public ResponseEntity<Page<ReservationResponse>> findReservationsByCamp(
      @PathVariable Long campId,
      @ParameterObject
          @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.ok(
        reservationService.findAllReservationsByCamp(campId, pageable, user.id()));
  }

  @Operation(summary = "예약 통계", description = "사업자 대시보드용 예약 현황 통계 (확정 예약 기준)")
  @PreAuthorize("hasRole('CAMP_OWNER')")
  @GetMapping("/stats")
  public ResponseEntity<ReservationStatsResponse> getReservationStats(
      @AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.ok(reservationService.getReservationStats(user.id()));
  }

  @Operation(summary = "월별 매출 통계", description = "사업자 대시보드 차트용 올해 월별 매출/예약 건수 (확정 예약 기준, 12개월 전체)")
  @PreAuthorize("hasRole('CAMP_OWNER')")
  @GetMapping("/stats/monthly")
  public ResponseEntity<List<MonthlyRevenueResponse>> getMonthlyRevenue(
      @AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.ok(reservationService.getMonthlyRevenue(user.id()));
  }
}
