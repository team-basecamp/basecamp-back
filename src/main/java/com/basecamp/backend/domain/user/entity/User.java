package com.basecamp.backend.domain.user.entity;

import com.basecamp.backend.common.enums.Role;
import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.util.StringUtils;

/**
 * 소셜 로그인 회원. {@code users} 테이블과 매핑된다.
 *
 * <p>{@code ddl-auto: validate} 이므로 컬럼 구성은 Flyway 최종 스키마(V1~V6)와 일치해야 한다. V3에서 {@code provider_id}
 * 가 제거되어 소셜 회원 식별은 {@code email}(UNIQUE)로 한다. 프로필 이미지({@code image_id} FK)는 별도 스텝에서 {@code images}
 * 연관으로 매핑한다.
 */
@Getter
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "user_id")
  private Long id;

  @Column(name = "nickname", nullable = false, length = 50)
  private String nickname;

  /**
   * 소셜 계정 이메일. 유니크 제약은 활성 회원에게만 걸린다(V9 의 {@code uq_users_email_active} 파생 컬럼). 탈퇴 회원의 행은 email 을
   * 그대로 보존하므로 여기에 {@code unique = true} 를 두면 실제 스키마와 어긋난다.
   */
  @Column(name = "email", nullable = false, length = 100)
  private String email;

  /**
   * 프로필 이미지({@code images} 참조, 회원당 1개). V3에서 users 가 FK({@code image_id})를 보유한다. 이미지 없는 회원도
   * 허용(NULL).
   */
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "image_id")
  private Image profileImage;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false, length = 20)
  private Provider provider;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private UserStatus status;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Column(name = "withdrawal_reason", length = 500)
  private String withdrawalReason;

  /** 관리자 제재 사유. 제재 중일 때만 값이 있다(해제 시 NULL 로 되돌린다). */
  @Column(name = "blacklist_reason", length = 200)
  private String blacklistReason;

  /** 관리자 제재 일시. 제재 중일 때만 값이 있다. */
  @Column(name = "blacklisted_at")
  private LocalDateTime blacklistedAt;

  @Builder
  private User(
      String nickname,
      String email,
      Image profileImage,
      Provider provider,
      Role role,
      UserStatus status) {
    this.nickname = nickname;
    this.email = email;
    this.profileImage = profileImage;
    this.provider = provider;
    this.role = role;
    this.status = status;
  }

  /**
   * 소셜 최초 로그인 시 신규 회원을 생성한다. 기본 권한은 {@link Role#CUSTOMER}, 상태는 {@link UserStatus#ACTIVE}.
   *
   * @param profileImage 소셜 프로필 이미지(없으면 {@code null})
   */
  public static User register(
      String nickname, String email, Image profileImage, Provider provider) {
    // @Column(nullable=false)는 DB NULL만 막고 빈 문자열은 막지 못하므로, 잘못된 회원 생성 자체를 팩토리에서 차단한다.
    // (email 은 소셜 회원 식별의 유일 키다. 미제공/미동의는 로그인 서비스에서 사전 차단하지만, 여기서도 최종 방어한다.)
    if (!StringUtils.hasText(email)) {
      throw new BusinessException(ErrorCode.INVALID_EMAIL);
    }
    return User.builder()
        .nickname(nickname)
        .email(email)
        .profileImage(profileImage)
        .provider(provider)
        .role(Role.CUSTOMER)
        .status(UserStatus.ACTIVE)
        .build();
  }

  /** 프로필(닉네임 + 프로필 이미지)을 수정한다. 이미지를 제거하려면 {@code profileImage} 에 {@code null} 을 전달한다. */
  public void updateProfile(String nickname, Image profileImage) {
    this.nickname = nickname;
    this.profileImage = profileImage;
  }

  /**
   * 회원 탈퇴 처리(soft delete). 상태를 {@link UserStatus#WITHDRAWN} 로 바꾸고 탈퇴 시각·사유를 기록한다.
   *
   * @param clock 탈퇴 시각 산정에 사용할 시계(서버 타임존 의존 제거 및 테스트 용이성 확보를 위해 주입받는다)
   */
  public void withdraw(String reason, Clock clock) {
    this.status = UserStatus.WITHDRAWN;
    this.deletedAt = LocalDateTime.now(clock);
    this.withdrawalReason = reason;
  }

  /**
   * 캠핑업체({@link Role#CAMP_OWNER})로 승격한다. 관리자가 사업자 정보를 심사해 승인했을 때만 호출된다(#53).
   *
   * <p>{@code role} 은 access 토큰 클레임에 담기므로, 승격 후에도 사용자가 들고 있는 토큰은 최대 30분간 {@code CUSTOMER} 다. 호출하는
   * 쪽에서 {@code UserRevocationCache.revoke()} 로 구 토큰을 무효화해야 한다.
   */
  public void promoteToCampOwner() {
    if (this.role == Role.CAMP_OWNER) {
      throw new BusinessException(ErrorCode.ALREADY_CAMP_OWNER);
    }
    this.role = Role.CAMP_OWNER;
  }

  /**
   * 관리자 제재(강제 로그아웃) 처리. 사유와 시각을 함께 기록한다.
   *
   * <p>제재 중에는 소셜 로그인·토큰 재발급이 모두 차단되고, 이미 발급된 access 토큰은 인증 필터가 거부한다({@code UserRevocationCache}).
   *
   * @param clock 제재 시각 산정에 사용할 시계(서버 타임존 의존 제거 및 테스트 용이성 확보를 위해 주입받는다)
   */
  public void blacklist(String reason, Clock clock) {
    this.status = UserStatus.BLACKLISTED;
    this.blacklistReason = reason;
    this.blacklistedAt = LocalDateTime.now(clock);
  }

  /**
   * 제재 해제. 상태를 {@link UserStatus#ACTIVE} 로 복구하고 제재 정보를 지운다.
   *
   * <p>제재 중에는 토큰이 발급되지 않았으므로, 해제 후 사용자는 다시 로그인해야 한다.
   */
  public void activate() {
    this.status = UserStatus.ACTIVE;
    this.blacklistReason = null;
    this.blacklistedAt = null;
  }

  public boolean isWithdrawn() {
    return this.status == UserStatus.WITHDRAWN;
  }

  public boolean isBlacklisted() {
    return this.status == UserStatus.BLACKLISTED;
  }
}
