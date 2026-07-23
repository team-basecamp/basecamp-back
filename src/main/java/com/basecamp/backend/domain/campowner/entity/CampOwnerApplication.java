package com.basecamp.backend.domain.campowner.entity;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 캠핑업체 권한 승격 신청. {@code camp_owner_applications} 테이블과 매핑된다(V12, #53).
 *
 * <p>업체는 소셜로 {@code CUSTOMER} 가입한 뒤 사업자 정보를 제출하고, 관리자가 승인하면 {@code users.role} 이 {@code CAMP_OWNER}
 * 로 승격된다. 신청 이력이 남아 "누가 언제 누구를 승인했는가"를 추적할 수 있다.
 *
 * <p>{@code user_id} 와 {@code processed_by} 는 감사 목적의 참조라 연관 매핑 대신 식별자만 들고 있다 ({@code
 * TokenBlacklist} 와 같은 방식). 신청을 읽을 때 {@code User} 를 함께 조회할 이유가 없다.
 *
 * <p>DB 의 파생 컬럼({@code user_id_pending}, {@code business_number_approved})은 <b>매핑하지 않는다.</b> {@code
 * ddl-auto: validate} 는 매핑된 컬럼만 검사하고, 생성 컬럼을 Hibernate 가 INSERT/UPDATE 에 끼워넣어서도 안 된다(V9 주석과 동일한
 * 이유).
 */
@Getter
@Entity
@Table(name = "camp_owner_applications")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampOwnerApplication {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "application_id")
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "business_number", nullable = false, columnDefinition = "CHAR(10)")
  private String businessNumber;

  @Column(name = "business_name", nullable = false, length = 100)
  private String businessName;

  @Column(name = "representative_name", nullable = false, length = 50)
  private String representativeName;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ApplicationStatus status;

  /** 반려 사유. 반려된 신청에만 값이 있다. */
  @Column(name = "reject_reason", length = 200)
  private String rejectReason;

  /** 심사한 관리자. 승인·반려 시에만 값이 있다. */
  @Column(name = "processed_by")
  private Long processedBy;

  @Column(name = "processed_at")
  private LocalDateTime processedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  /**
   * 낙관적 락 버전(V13). 관리자 두 명이 같은 신청을 동시에 승인/반려할 때 나중 커밋이 앞선 심사 이력을 덮어쓰는 것을 막는다.
   *
   * <p>{@link #requirePending()} 만으로는 부족하다. {@code findById} 는 잠금 없는 스냅샷 읽기라 두 트랜잭션이 모두 {@code
   * PENDING} 을 보고 검사를 통과한 뒤, 나중에 커밋한 쪽이 {@code status}·{@code processedBy}· {@code processedAt} 을
   * 덮어쓴다. 버전이 어긋난 두 번째 UPDATE 는 0건이 되어 실패한다 ({@code ObjectOptimisticLockingFailureException} →
   * {@code C005}).
   */
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  private CampOwnerApplication(
      Long userId, String businessNumber, String businessName, String representativeName) {
    this.userId = userId;
    this.businessNumber = businessNumber;
    this.businessName = businessName;
    this.representativeName = representativeName;
    this.status = ApplicationStatus.PENDING;
  }

  /** 새 신청을 생성한다. 상태는 항상 {@link ApplicationStatus#PENDING} 에서 시작한다. */
  public static CampOwnerApplication submit(
      Long userId, String businessNumber, String businessName, String representativeName) {
    return new CampOwnerApplication(userId, businessNumber, businessName, representativeName);
  }

  /**
   * 승인 처리. 심사 중인 신청만 처리할 수 있다.
   *
   * @param clock 처리 시각 산정에 사용할 시계(서버 타임존 의존 제거 및 테스트 용이성 확보를 위해 주입받는다)
   */
  public void approve(Long adminId, Clock clock) {
    requirePending();
    this.status = ApplicationStatus.APPROVED;
    this.processedBy = adminId;
    this.processedAt = LocalDateTime.now(clock);
  }

  /** 반려 처리. 심사 중인 신청만 처리할 수 있다. */
  public void reject(Long adminId, String reason, Clock clock) {
    requirePending();
    this.status = ApplicationStatus.REJECTED;
    this.rejectReason = reason;
    this.processedBy = adminId;
    this.processedAt = LocalDateTime.now(clock);
  }

  public boolean isPending() {
    return this.status == ApplicationStatus.PENDING;
  }

  /**
   * 이미 승인·반려된 신청을 다시 처리하면 심사 이력(처리자·처리 시각)이 덮인다.
   *
   * <p><b>이건 순차 재요청만 막는다.</b> 관리자가 승인 버튼을 두 번 누른 경우처럼 앞선 트랜잭션이 이미 커밋된 상황에서 {@code CO004} 를 돌려준다. 두
   * 요청이 <i>겹치면</i> 둘 다 {@code PENDING} 을 읽고 여기를 통과하므로, 진짜 동시성 방어는 {@link #version} 의 낙관적 락이 한다.
   */
  private void requirePending() {
    if (!isPending()) {
      throw new BusinessException(ErrorCode.CAMP_OWNER_APPLICATION_ALREADY_PROCESSED);
    }
  }
}
