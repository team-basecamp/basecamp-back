package com.basecamp.backend.domain.reservation.entity;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    // --- 연관 관계 매핑 ---

    // 외래키 fk_rsv_user (user_id 참조)
    /*
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 외래키 fk_rsv_camp (camp_id 참조)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camp_id", nullable = false)
    private Camp camp;
     */
    // TODO: 임시 엔티티 (삭제해야 할 것)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "camp_id")
    private Long campId;

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

    @Column(name = "cancel_at") //TODO: cancel_date -> canceled_at 변경 필요
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

    // 동시에 들어온 수락/거절 요청으로 상태가 뒤엉키는 것을 막기 위한 낙관적 락
    @Version
    @Column(name = "version")
    private Long version;

    // --- 비즈니스 메서드 (상태 변경 도메인 로직) ---

    public void pend(){
        this.status = ReservationStatus.PENDING;
    }

    public void approve() {
        if (this.status != ReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_PENDING);
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
        if(this.status == ReservationStatus.CANCELLED || this.status == ReservationStatus.REJECTED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED_OR_REJECTED);
        }

        this.status = ReservationStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
        this.cancelAt = LocalDateTime.now();
    }

}


