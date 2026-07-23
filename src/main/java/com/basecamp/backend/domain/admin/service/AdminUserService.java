package com.basecamp.backend.domain.admin.service;

import com.basecamp.backend.common.enums.Role;
import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.admin.dto.response.AdminUserResponse;
import com.basecamp.backend.domain.admin.dto.response.BlacklistedUserResponse;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.entity.UserStatus;
import com.basecamp.backend.domain.user.repository.UserRepository;
import com.basecamp.backend.domain.user.repository.UserSpecs;
import com.basecamp.backend.security.cache.UserRevocationCache;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 회원 조회 및 제재(강제 로그아웃) / 해제.
 *
 * <p>제재는 토큰 단위가 아니라 회원 단위다. 무상태 JWT 라 서버가 발급된 토큰을 보관하지 않아 대상 회원의 {@code jti} 를 알 수 없기 때문이다(#18). 영속
 * 기록은 {@code users.status = BLACKLISTED}, 인증 필터가 매 요청 조회하는 표시는 {@link UserRevocationCache}(Redis)가
 * 담당한다.
 *
 * <p>제재 중에는 세 경로가 모두 막힌다: access 토큰(필터), 토큰 재발급, 소셜 재로그인.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AdminUserService {

  private final UserRepository userRepository;
  private final UserRevocationCache userRevocationCache;
  private final Clock clock;

  /**
   * 회원을 제재하고 이미 발급된 access 토큰을 즉시 무효화한다.
   *
   * <p><b>Redis 를 먼저 쓴다.</b> 뒤이어 DB 가 롤백되면 캐시에만 표시가 남지만, 그건 "죽지 않아야 할 토큰이 죽는" 방향이라 안전하고 TTL 로 자동
   * 정리된다. 반대 순서였다면 커밋과 캐시 반영 사이에 제재된 회원의 요청이 통과한다.
   */
  public void blacklistUser(Long userId, String reason) {
    User user = findActiveUser(userId);
    if (user.isBlacklisted()) {
      throw new BusinessException(ErrorCode.USER_ALREADY_BLACKLISTED);
    }

    userRevocationCache.revoke(userId);
    user.blacklist(reason, clock);
  }

  /**
   * 제재를 해제한다. 사용자는 다시 로그인해야 한다(제재 중에는 토큰이 발급되지 않았다).
   *
   * <p><b>DB 를 먼저 바꾼다.</b> Redis 삭제가 실패하면 예외가 전파되어 트랜잭션이 롤백되고 제재 상태가 유지된다. 반대 순서였다면 캐시만 지워져 제재가 사실상
   * 풀린 채 DB 는 BLACKLISTED 로 남는다.
   */
  public void releaseUser(Long userId) {
    User user = findActiveUser(userId);
    if (!user.isBlacklisted()) {
      throw new BusinessException(ErrorCode.USER_NOT_BLACKLISTED);
    }

    user.activate();
    userRevocationCache.clear(userId);
  }

  /**
   * 관리자 회원 목록 조회(#19). 세 조건은 모두 선택이며 지정된 것만 AND 로 묶인다.
   *
   * <p>필터가 하나도 없으면 탈퇴 회원까지 포함한 전체가 조회된다. 탈퇴 회원을 감추면 관리자가 전체 현황을 볼 수단이 없어지므로, 걸러내고 싶을 때 {@code
   * status} 를 지정하는 쪽으로 뒀다.
   */
  @Transactional(readOnly = true)
  public Page<AdminUserResponse> findUsers(
      UserStatus status, Role role, String keyword, Pageable pageable) {
    Specification<User> spec =
        Specification.where(UserSpecs.statusEquals(status))
            .and(UserSpecs.roleEquals(role))
            .and(UserSpecs.keywordContains(keyword));

    return userRepository.findAll(spec, pageable).map(AdminUserResponse::from);
  }

  @Transactional(readOnly = true)
  public Page<BlacklistedUserResponse> findBlacklistedUsers(Pageable pageable) {
    return userRepository
        .findByStatus(UserStatus.BLACKLISTED, pageable)
        .map(BlacklistedUserResponse::from);
  }

  /** 탈퇴 회원은 제재 대상이 아니다(이미 서비스를 이용할 수 없다). */
  private User findActiveUser(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    if (user.isWithdrawn()) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    return user;
  }
}
