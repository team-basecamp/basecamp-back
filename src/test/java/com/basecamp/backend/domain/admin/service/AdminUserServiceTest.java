package com.basecamp.backend.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.UserRepository;
import com.basecamp.backend.security.cache.UserRevocationCache;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant NOW = Instant.parse("2026-07-09T00:00:00Z");
  private static final Long USER_ID = 7L;
  private static final String REASON = "부적절한 게시글 반복 작성";

  @Mock private UserRepository userRepository;

  @Mock private UserRevocationCache userRevocationCache;

  private AdminUserService adminUserService;

  @BeforeEach
  void setUp() {
    adminUserService =
        new AdminUserService(userRepository, userRevocationCache, Clock.fixed(NOW, ZONE));
  }

  private User activeUser() {
    return User.register("camper", "user@example.com", null, Provider.KAKAO);
  }

  private User blacklistedUser() {
    User user = activeUser();
    user.blacklist(REASON, Clock.fixed(NOW, ZONE));
    return user;
  }

  @Test
  @DisplayName("blacklistUser_정상_상태와사유를기록하고_access토큰을무효화한다")
  void blacklistUser_정상_상태와사유를기록하고_access토큰을무효화한다() {
    // given
    User user = activeUser();
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

    // when
    adminUserService.blacklistUser(USER_ID, REASON);

    // then
    verify(userRevocationCache).revoke(USER_ID);
    assertThat(user.isBlacklisted()).isTrue();
    assertThat(user.getBlacklistReason()).isEqualTo(REASON);
    assertThat(user.getBlacklistedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZONE));
  }

  @Test
  @DisplayName("blacklistUser_Redis쓰기실패_회원상태를바꾸지않는다")
  void blacklistUser_Redis쓰기실패_회원상태를바꾸지않는다() {
    // given: Redis 를 먼저 써야 커밋과 캐시 반영 사이에 제재된 회원의 요청이 통과하는 창이 열리지 않는다.
    User user = activeUser();
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    willThrow(new RedisConnectionFailureException("down"))
        .given(userRevocationCache)
        .revoke(USER_ID);

    // when & then: 예외가 전파되어 트랜잭션이 롤백된다.
    assertThatThrownBy(() -> adminUserService.blacklistUser(USER_ID, REASON))
        .isInstanceOf(RedisConnectionFailureException.class);
    assertThat(user.isBlacklisted()).isFalse();
  }

  @Test
  @DisplayName("blacklistUser_이미제재된회원_U003을던진다")
  void blacklistUser_이미제재된회원_U003을던진다() {
    // given
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(blacklistedUser()));

    // when & then
    assertBusinessException(
        () -> adminUserService.blacklistUser(USER_ID, REASON), ErrorCode.USER_ALREADY_BLACKLISTED);
    verify(userRevocationCache, never()).revoke(USER_ID);
  }

  @Test
  @DisplayName("blacklistUser_존재하지않는회원_U002를던진다")
  void blacklistUser_존재하지않는회원_U002를던진다() {
    // given
    given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

    // when & then
    assertBusinessException(
        () -> adminUserService.blacklistUser(USER_ID, REASON), ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("blacklistUser_탈퇴한회원_U002를던진다")
  void blacklistUser_탈퇴한회원_U002를던진다() {
    // given: 이미 서비스를 이용할 수 없는 회원은 제재 대상이 아니다.
    User withdrawn = activeUser();
    withdrawn.withdraw("사유", Clock.fixed(NOW, ZONE));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(withdrawn));

    // when & then
    assertBusinessException(
        () -> adminUserService.blacklistUser(USER_ID, REASON), ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("releaseUser_정상_상태를복구하고_제재정보와캐시를지운다")
  void releaseUser_정상_상태를복구하고_제재정보와캐시를지운다() {
    // given
    User user = blacklistedUser();
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

    // when
    adminUserService.releaseUser(USER_ID);

    // then: Redis 삭제가 실패하면 트랜잭션이 롤백되어 제재 상태가 유지되어야 하므로 DB 변경을 먼저 한다.
    assertThat(user.isBlacklisted()).isFalse();
    assertThat(user.getBlacklistReason()).isNull();
    assertThat(user.getBlacklistedAt()).isNull();
    verify(userRevocationCache).clear(USER_ID);
  }

  @Test
  @DisplayName("releaseUser_제재상태가아닌회원_U004를던진다")
  void releaseUser_제재상태가아닌회원_U004를던진다() {
    // given
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser()));

    // when & then
    assertBusinessException(
        () -> adminUserService.releaseUser(USER_ID), ErrorCode.USER_NOT_BLACKLISTED);
    verify(userRevocationCache, never()).clear(USER_ID);
  }

  private void assertBusinessException(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(expected);
  }
}
