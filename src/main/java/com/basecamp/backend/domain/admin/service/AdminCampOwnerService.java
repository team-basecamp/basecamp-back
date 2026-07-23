package com.basecamp.backend.domain.admin.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.campowner.dto.response.CampOwnerApplicationResponse;
import com.basecamp.backend.domain.campowner.entity.ApplicationStatus;
import com.basecamp.backend.domain.campowner.entity.CampOwnerApplication;
import com.basecamp.backend.domain.campowner.repository.CampOwnerApplicationRepository;
import com.basecamp.backend.domain.notification.entity.NotificationType;
import com.basecamp.backend.domain.notification.event.NotificationEvent;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.UserRepository;
import com.basecamp.backend.security.cache.UserRevocationCache;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 캠핑업체 권한 승격 심사(#53).
 *
 * <p>승인은 <b>신청 상태 변경 + 회원 권한 승격 + 구 access 토큰 무효화</b>가 한 트랜잭션으로 묶인다. 셋 중 하나라도 실패하면 전부 되돌아가야 한다 —
 * "승격됐는데 구 토큰이 살아 있는" 어중간한 상태를 만들지 않는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AdminCampOwnerService {

  private final CampOwnerApplicationRepository applicationRepository;
  private final UserRepository userRepository;
  private final UserRevocationCache userRevocationCache;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Page<CampOwnerApplicationResponse> findApplications(
      ApplicationStatus status, Pageable pageable) {
    return applicationRepository
        .findByStatus(status, pageable)
        .map(CampOwnerApplicationResponse::from);
  }

  /**
   * 신청을 승인하고 회원을 {@code CAMP_OWNER} 로 승격한다.
   *
   * <p><b>DB 변경을 flush 로 먼저 검증하고 Redis 를 나중에 쓴다.</b> {@code role} 은 access 토큰 클레임에 들어 있어 승격 직후에도 구
   * 토큰은 {@code CUSTOMER} 다. 무효화해야 사용자가 재로그인해 새 권한을 받는다. 다만 사업자번호 유니크 위반({@code
   * uq_coa_biznum_approved})·낙관적 락은 커밋 시점에 터지므로, {@code flush()} 로 앞당겨 검증한다. 그래야 승격이 확정된 뒤에만 무효화하고,
   * 승격이 실패하면 {@code revoke()} 로 회원을 무효화하는 일 자체가 일어나지 않는다(#119 후속: 실패한 승격이 회원을 블랙리스트로 남기던 문제).
   * {@code revoke()} 가 실패하면 예외가 전파되어 승격도 함께 롤백된다.
   *
   * <p>제재 회원({@code blacklistUser})과 순서가 반대인 이유: 제재는 <b>권한 축소</b>라 커밋과 캐시 반영 사이의 빈틈으로 제재된 회원의 요청이
   * 통과하면 안 된다. 승격은 <b>권한 확대</b>라 그 빈틈에서 구 토큰이 살아남아도 {@code CUSTOMER} 권한일 뿐이라 무해하다(자세한 근거는 {@code
   * docs/decisions/camp-owner-promotion.md} §5).
   */
  public void approve(Long applicationId, Long adminId) {
    CampOwnerApplication application = findApplication(applicationId);
    User user = findActiveUser(application.getUserId());

    application.approve(adminId, clock); // PENDING 아니면 CO004
    user.promoteToCampOwner(); // 이미 CAMP_OWNER 면 CO003

    // 사업자번호 유니크 위반·낙관적 락을 커밋까지 미루지 않고 지금 확인한다. 커밋 시점에 터지면 아래 revoke 가
    // 이미 실행돼 "승격은 실패했는데 회원만 무효화된" 상태가 남는다. 여기서 끊으면 revoke 는 실행되지 않는다.
    try {
      applicationRepository.flush();
    } catch (DataIntegrityViolationException e) {
      // 이미 같은 사업자번호로 승인된 업체가 있다. 원본 500 대신 명시적 409 로 변환한다.
      throw new BusinessException(ErrorCode.BUSINESS_NUMBER_ALREADY_APPROVED);
    }

    // 승격이 DB 에 안전히 반영된 뒤에만 구 토큰을 무효화한다.
    userRevocationCache.revoke(user.getId());

    // 커밋 이후 신청자에게 승인 알림 (AFTER_COMMIT 리스너가 저장·push)
    eventPublisher.publishEvent(
        NotificationEvent.of(
            application.getUserId(),
            NotificationType.CAMP_OWNER_APPROVED,
            application.getId(),
            null));
  }

  /** 신청을 반려한다. 회원 권한은 그대로이므로 토큰을 건드리지 않는다. */
  public void reject(Long applicationId, Long adminId, String reason) {
    CampOwnerApplication application = findApplication(applicationId);
    application.reject(adminId, reason, clock);

    // 커밋 이후 신청자에게 반려 알림 (AFTER_COMMIT 리스너가 저장·push)
    eventPublisher.publishEvent(
        NotificationEvent.of(
            application.getUserId(),
            NotificationType.CAMP_OWNER_REJECTED,
            application.getId(),
            null));
  }

  private CampOwnerApplication findApplication(Long applicationId) {
    return applicationRepository
        .findById(applicationId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_OWNER_APPLICATION_NOT_FOUND));
  }

  /** 탈퇴 회원은 승격 대상이 아니다. 제재 회원도 마찬가지다 — 제재를 풀지 않은 채 권한만 올리면 해제되는 순간 업체 권한을 그대로 갖게 된다. */
  private User findActiveUser(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    if (user.isWithdrawn()) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    if (user.isBlacklisted()) {
      throw new BusinessException(ErrorCode.BLACKLISTED_USER);
    }
    return user;
  }
}
