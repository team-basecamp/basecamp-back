package com.basecamp.backend.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 무효화된 JWT 기록. {@code token_blacklist} 테이블과 매핑된다.
 *
 * <p>무상태 JWT 는 서명이 유효하면 만료 전까지 거부할 수단이 없다. 로그아웃 · 탈퇴 · refresh 회전처럼 아직 살아 있는 토큰을 강제로 죽여야 하는 예외 상황에만
 * 레코드를 남기는 denylist 다(#39).
 *
 * <p>조회 키는 토큰 원문이 아니라 {@code jti}(UUID) 다. V8 에서 인덱스를 추가했으며, 원문을 저장하지 않으므로 DB 유출 시 유효한 토큰이 함께 새지
 * 않는다.
 *
 * <p>{@code user_id} 는 감사(audit) 목적의 참조라 연관 매핑 대신 식별자만 들고 있다. 등록 시 {@code User} 를 추가로 조회할 이유가 없다.
 */
@Getter
@Entity
@Table(name = "token_blacklist")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenBlacklist {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "blacklist_id")
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "jti", nullable = false, unique = true, columnDefinition = "CHAR(36)")
  private String jti;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", length = 100)
  private BlacklistReason reason;

  @CreatedDate
  @Column(name = "blacklisted_at", nullable = false, updatable = false)
  private LocalDateTime blacklistedAt;

  /**
   * 원 토큰의 만료 시각. 이 시각이 지나면 서명 검증 단계에서 이미 걸러지므로 레코드를 정리해도 된다 (정리 스케줄러는 {@code idx_bl_expires} 를
   * 사용한다).
   */
  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  private TokenBlacklist(Long userId, String jti, BlacklistReason reason, LocalDateTime expiresAt) {
    this.userId = userId;
    this.jti = jti;
    this.reason = reason;
    this.expiresAt = expiresAt;
  }

  public static TokenBlacklist of(
      Long userId, String jti, BlacklistReason reason, LocalDateTime expiresAt) {
    return new TokenBlacklist(userId, jti, reason, expiresAt);
  }
}
