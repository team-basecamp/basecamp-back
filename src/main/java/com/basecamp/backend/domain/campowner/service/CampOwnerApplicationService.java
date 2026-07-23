package com.basecamp.backend.domain.campowner.service;

import com.basecamp.backend.common.enums.Role;
import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.campowner.dto.request.CampOwnerApplicationRequest;
import com.basecamp.backend.domain.campowner.dto.response.CampOwnerApplicationResponse;
import com.basecamp.backend.domain.campowner.entity.ApplicationStatus;
import com.basecamp.backend.domain.campowner.entity.CampOwnerApplication;
import com.basecamp.backend.domain.campowner.repository.CampOwnerApplicationRepository;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 캠핑업체 권한 승격 신청(#53).
 *
 * <p>신청은 심사를 요청할 뿐 권한을 바꾸지 않는다. 실제 승격은 관리자가 승인할 때 {@code AdminCampOwnerService} 에서 일어난다.
 *
 * <p><b>사업자등록번호는 자릿수(숫자 10자리)만 검증한다.</b> 체크섬을 맞춰봐야 오타를 걸러낼 뿐 사업자가 실재하는지는 알 수 없고, 그 판단은 어차피 관리자 심사가
 * 한다. 진위 확인이 필요해지면 국세청 API 를 붙인다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CampOwnerApplicationService {

  private final CampOwnerApplicationRepository applicationRepository;
  private final UserRepository userRepository;

  /**
   * 업체 전환을 신청한다.
   *
   * <p><b>동시 신청 방어:</b> {@code existsByUserIdAndStatus} 조회와 INSERT 사이에 다른 요청이 끼어들 수 있다. 최종 방어선은 DB
   * 의 {@code uq_coa_user_pending} 유니크 제약이고, 위반은 "이미 심사 중"과 같은 상황이므로 {@code CO002} 로 변환한다. 제약 위반을 즉시
   * 보려면 {@code saveAndFlush} 여야 한다 (지연 flush 면 트랜잭션 커밋 시점에 터져 여기서 잡지 못한다).
   */
  public CampOwnerApplicationResponse apply(Long userId, CampOwnerApplicationRequest request) {
    User user = findActiveUser(userId);
    if (user.getRole() == Role.CAMP_OWNER) {
      throw new BusinessException(ErrorCode.ALREADY_CAMP_OWNER);
    }
    if (applicationRepository.existsByUserIdAndStatus(userId, ApplicationStatus.PENDING)) {
      throw new BusinessException(ErrorCode.CAMP_OWNER_APPLICATION_ALREADY_PENDING);
    }

    CampOwnerApplication application =
        CampOwnerApplication.submit(
            userId, request.businessNumber(), request.businessName(), request.representativeName());
    try {
      return CampOwnerApplicationResponse.from(applicationRepository.saveAndFlush(application));
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.CAMP_OWNER_APPLICATION_ALREADY_PENDING);
    }
  }

  /** 본인의 최신 신청 상태. 반려 후 재신청했다면 가장 최근 건을 돌려준다. */
  @Transactional(readOnly = true)
  public CampOwnerApplicationResponse findMyLatestApplication(Long userId) {
    return applicationRepository
        .findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
        .map(CampOwnerApplicationResponse::from)
        .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_OWNER_APPLICATION_NOT_FOUND));
  }

  /** 탈퇴·제재 회원은 신청할 수 없다. 제재 중에는 애초에 access 토큰이 인증 필터에서 거부되지만 여기서도 막는다. */
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
