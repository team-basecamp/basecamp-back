package com.basecamp.backend.domain.reservation.entity;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "reservation_id")
  private Long id;

  // --- 연관 관계 매핑 ---

  // 외래키 fk_rsv_user (user_id 참조)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  // 외래키 fk_rsv_camp (camp_id 참조)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "camp_id", nullable = false)
  private Camp camp;

  // --- 기본 예약 필드 ---

  @Column(name = "check_in_date", nullable = false)
  private LocalDate checkInDate;

  @Column(name = "check_out_date", nullable = false)
  private LocalDate checkOutDate;

  @Column(name = "guest_count", nullable = false)
  private int guestCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ReservationStatus status; // PENDING, RESERVED, REJECTED, CANCELLED

  @Column(name = "reject_reason", length = 200)
  private String rejectReason;

  @Column(name = "cancel_at")
  private LocalDateTime cancelAt;

  @Column(name = "total_price", nullable = false)
  private Long totalPrice;

  @Column(name = "customer_name", nullable = false)
  private String customerName;

  @Column(name = "customer_phone", nullable = false)
  private String customerPhone;

  @Column(name = "special_request")
  private String specialRequest;

  // --- 생성 및 수정 일시 (자동화 대신 명시적 선언 또는 @CreatedDate 활용 가능) ---
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  // pending 상태에서 24시간 안에 업체가 수락하지 않는다면 -> REJECTED(예약거절), REFUNDED(환불) -> rejected_reason:"업체 미응답
  // 자동 반려" response
  @Column(name = "expired_at")
  private LocalDateTime expiredAt;

  // 동시에 들어온 수락/거절 요청으로 상태가 뒤엉키는 것을 막기 위한 낙관적 락
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Builder
  private Reservation(
      User user,
      Camp camp,
      LocalDate checkInDate,
      LocalDate checkOutDate,
      int guestCount,
      ReservationStatus status,
      Long totalPrice,
      String customerName,
      String customerPhone,
      String specialRequest,
      LocalDateTime createdAt) {
    this.user = user;
    this.camp = camp;
    this.checkInDate = checkInDate;
    this.checkOutDate = checkOutDate;
    this.guestCount = guestCount;
    this.status = status;
    this.totalPrice = totalPrice;
    this.customerName = customerName;
    this.customerPhone = customerPhone;
    this.specialRequest = specialRequest;
    this.createdAt = createdAt;
  }

  // --- 비즈니스 메서드 (상태 변경 도메인 로직) ---

  public void pend() {
    this.status = ReservationStatus.PENDING;
  }

  // PG 결제 확인 후 호출 (PENDING_PAYMENT -> PENDING)
  public void confirmPayment() {
    if (this.status != ReservationStatus.PENDING_PAYMENT) {
      throw new BusinessException(ErrorCode.RESERVATION_NOT_PENDING_PAYMENT);
    }

    LocalDateTime now = LocalDateTime.now();
    this.status = ReservationStatus.PENDING;
    this.expiredAt = now.plusHours(24);
    this.updatedAt = now;
  }

  public void approve() {
    if (this.status != ReservationStatus.PENDING) {
      throw new BusinessException(ErrorCode.RESERVATION_NOT_PENDING);
    }

    if (this.expiredAt != null && this.expiredAt.isBefore(LocalDateTime.now())) {
      throw new BusinessException(ErrorCode.RESERVATION_EXPIRED);
    }

    this.status = ReservationStatus.RESERVED;
    this.updatedAt = LocalDateTime.now();
  }

  public void reject(String reason) {
    if (this.status != ReservationStatus.PENDING) {
      throw new BusinessException(ErrorCode.RESERVATION_NOT_PENDING);
    }

    this.status = ReservationStatus.REJECTED;
    this.updatedAt = LocalDateTime.now();
    this.rejectReason = reason;
  }

  public void cancel() {
    if (this.status == ReservationStatus.CANCELLED || this.status == ReservationStatus.REJECTED) {
      throw new BusinessException(ErrorCode.ALREADY_CANCELED_OR_REJECTED);
    }

    this.status = ReservationStatus.CANCELLED;
    this.updatedAt = LocalDateTime.now();
    this.cancelAt = LocalDateTime.now();
  }
}
