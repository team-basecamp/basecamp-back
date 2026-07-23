package com.basecamp.backend.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.basecamp.backend.common.enums.Role;
import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.campowner.entity.ApplicationStatus;
import com.basecamp.backend.domain.campowner.entity.CampOwnerApplication;
import com.basecamp.backend.domain.campowner.repository.CampOwnerApplicationRepository;
import com.basecamp.backend.domain.notification.entity.NotificationType;
import com.basecamp.backend.domain.notification.event.NotificationEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 관리자의 캠핑업체 승격 심사 단위 테스트(#53).
 *
 * <p>승인의 핵심은 "권한을 올리고, 구 토큰을 반드시 무효화하는 것"이다. role 은 access 토큰 클레임에 들어 있어 무효화하지 않으면 최대 30분간 승격이 반영되지
 * 않는다.
 */
@ExtendWith(MockitoExtension.class)
class AdminCampOwnerServiceTest {

  private static final Long APPLICATION_ID = 3L;
  private static final Long USER_ID = 7L;
  private static final Long ADMIN_ID = 9L;
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

  @Mock private CampOwnerApplicationRepository applicationRepository;

  @Mock private UserRepository userRepository;

  @Mock private UserRevocationCache userRevocationCache;

  @Mock private ApplicationEventPublisher eventPublisher;

  private AdminCampOwnerService adminCampOwnerService;

  @BeforeEach
  void setUp() {
    adminCampOwnerService =
        new AdminCampOwnerService(
            applicationRepository,
            userRepository,
            userRevocationCache,
            eventPublisher,
            Clock.fixed(NOW, ZONE));
  }

  private User activeUser() {
    User user = User.register("camper", "user@example.com", null, Provider.KAKAO);
    ReflectionTestUtils.setField(user, "id", USER_ID);
    return user;
  }

  private CampOwnerApplication pendingApplication() {
    CampOwnerApplication application =
        CampOwnerApplication.submit(USER_ID, "1234567890", "베이스캠프 오토캠핑장", "홍길동");
    ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
    return application;
  }

  @Test
  @DisplayName("approve_정상_CAMP_OWNER로승격하고_구토큰을무효화한다")
  void approve_정상_승격하고_구토큰을무효화한다() {
    // given
    CampOwnerApplication application = pendingApplication();
    User user = activeUser();
    given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.of(application));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

    // when
    adminCampOwnerService.approve(APPLICATION_ID, ADMIN_ID);

    // then
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
    assertThat(application.getProcessedBy()).isEqualTo(ADMIN_ID);
    assertThat(application.getProcessedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZONE));
    assertThat(user.getRole()).isEqualTo(Role.CAMP_OWNER);

    // role 은 토큰 클레임에 있다. 무효화하지 않으면 최대 30분간 CUSTOMER 로 남는다.
    verify(userRevocationCache).revoke(USER_ID);

    // 신청자에게 승인 알림 이벤트가 발행된다(#92).
    verify(eventPublisher)
        .publishEvent(
            NotificationEvent.of(
                USER_ID, NotificationType.CAMP_OWNER_APPROVED, APPLICATION_ID, null));
  }

  @Test
  @DisplayName("approve_이미처리된신청_CO004를던지고_토큰을건드리지않는다")
  void approve_이미처리된신청_CO004를던진다() {
    // given: 관리자 두 명이 동시에 승인 버튼을 누른 상황.
    CampOwnerApplication application = pendingApplication();
    application.approve(ADMIN_ID, Clock.fixed(NOW, ZONE));
    given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.of(application));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser()));

    // when & then
    assertBusinessException(
        () -> adminCampOwnerService.approve(APPLICATION_ID, ADMIN_ID),
        ErrorCode.CAMP_OWNER_APPLICATION_ALREADY_PROCESSED);
    verify(userRevocationCache, never()).revoke(anyLong());
  }

  @Test
  @DisplayName("approve_존재하지않는신청_CO001을던진다")
  void approve_존재하지않는신청_CO001을던진다() {
    // given
    given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.empty());

    // when & then
    assertBusinessException(
        () -> adminCampOwnerService.approve(APPLICATION_ID, ADMIN_ID),
        ErrorCode.CAMP_OWNER_APPLICATION_NOT_FOUND);
    verify(userRevocationCache, never()).revoke(anyLong());
  }

  @Test
  @DisplayName("approve_탈퇴한회원의신청_U002를던진다")
  void approve_탈퇴한회원의신청_U002를던진다() {
    // given: 신청 후 탈퇴한 회원을 승격시켜서는 안 된다.
    User withdrawn = activeUser();
    withdrawn.withdraw("사유", Clock.fixed(NOW, ZONE));
    given(applicationRepository.findById(APPLICATION_ID))
        .willReturn(Optional.of(pendingApplication()));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(withdrawn));

    // when & then
    assertBusinessException(
        () -> adminCampOwnerService.approve(APPLICATION_ID, ADMIN_ID), ErrorCode.USER_NOT_FOUND);
    verify(userRevocationCache, never()).revoke(anyLong());
  }

  @Test
  @DisplayName("approve_제재된회원의신청_A007을던진다")
  void approve_제재된회원의신청_A007을던진다() {
    // given: 제재를 풀지 않은 채 권한만 올리면, 해제되는 순간 업체 권한을 그대로 갖게 된다.
    User blacklisted = activeUser();
    blacklisted.blacklist("어뷰징", Clock.fixed(NOW, ZONE));
    given(applicationRepository.findById(APPLICATION_ID))
        .willReturn(Optional.of(pendingApplication()));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(blacklisted));

    // when & then
    assertBusinessException(
        () -> adminCampOwnerService.approve(APPLICATION_ID, ADMIN_ID), ErrorCode.BLACKLISTED_USER);
    verify(userRevocationCache, never()).revoke(anyLong());
  }

  @Test
  @DisplayName("approve_이미캠핑업체인회원_CO003을던진다")
  void approve_이미캠핑업체인회원_CO003을던진다() {
    // given: 방어적 검사. 승격된 회원의 PENDING 신청이 남아 있는 상황.
    User owner = activeUser();
    owner.promoteToCampOwner();
    given(applicationRepository.findById(APPLICATION_ID))
        .willReturn(Optional.of(pendingApplication()));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(owner));

    // when & then
    assertBusinessException(
        () -> adminCampOwnerService.approve(APPLICATION_ID, ADMIN_ID),
        ErrorCode.ALREADY_CAMP_OWNER);
    verify(userRevocationCache, never()).revoke(anyLong());
  }

  @Test
  @DisplayName("approve_이미승인된사업자번호_CO005를던지고_토큰을건드리지않는다")
  void approve_이미승인된사업자번호_CO005를던진다() {
    // given: 다른 신청이 같은 사업자번호로 이미 승인돼, flush 시 유니크 제약(uq_coa_biznum_approved)이 터진다.
    given(applicationRepository.findById(APPLICATION_ID))
        .willReturn(Optional.of(pendingApplication()));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser()));
    org.mockito.BDDMockito.willThrow(new DataIntegrityViolationException("dup biznum"))
        .given(applicationRepository)
        .flush();

    // when & then: 승격이 확정되지 않았으므로 회원을 무효화(블랙리스트)해서는 안 된다.
    assertBusinessException(
        () -> adminCampOwnerService.approve(APPLICATION_ID, ADMIN_ID),
        ErrorCode.BUSINESS_NUMBER_ALREADY_APPROVED);
    verify(userRevocationCache, never()).revoke(anyLong());
  }

  @Test
  @DisplayName("approve_Redis쓰기실패_예외를전파해_승격을롤백시킨다")
  void approve_Redis쓰기실패_예외를전파한다() {
    // given: 캐시 쓰기가 실패하면 "승격됐는데 구 토큰이 살아 있는" 상태가 된다. 삼키면 안 된다.
    given(applicationRepository.findById(APPLICATION_ID))
        .willReturn(Optional.of(pendingApplication()));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser()));
    org.mockito.BDDMockito.willThrow(new RuntimeException("redis down"))
        .given(userRevocationCache)
        .revoke(USER_ID);

    // when & then
    assertThatThrownBy(() -> adminCampOwnerService.approve(APPLICATION_ID, ADMIN_ID))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("reject_정상_사유와처리자를기록하고_토큰을건드리지않는다")
  void reject_정상_사유와처리자를기록한다() {
    // given
    CampOwnerApplication application = pendingApplication();
    given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.of(application));

    // when
    adminCampOwnerService.reject(APPLICATION_ID, ADMIN_ID, "사업자등록증 확인 불가");

    // then: 반려는 권한을 바꾸지 않으므로 기존 토큰이 그대로 유효하다.
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    assertThat(application.getRejectReason()).isEqualTo("사업자등록증 확인 불가");
    assertThat(application.getProcessedBy()).isEqualTo(ADMIN_ID);
    verify(userRevocationCache, never()).revoke(anyLong());

    // 신청자에게 반려 알림 이벤트가 발행된다(#92).
    verify(eventPublisher)
        .publishEvent(
            NotificationEvent.of(
                USER_ID, NotificationType.CAMP_OWNER_REJECTED, APPLICATION_ID, null));
  }

  @Test
  @DisplayName("reject_이미처리된신청_CO004를던진다")
  void reject_이미처리된신청_CO004를던진다() {
    // given
    CampOwnerApplication application = pendingApplication();
    application.reject(ADMIN_ID, "1차 반려", Clock.fixed(NOW, ZONE));
    given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.of(application));

    // when & then: 이미 기록된 심사 이력(처리자·시각)이 덮이면 안 된다.
    assertBusinessException(
        () -> adminCampOwnerService.reject(APPLICATION_ID, ADMIN_ID, "2차 반려"),
        ErrorCode.CAMP_OWNER_APPLICATION_ALREADY_PROCESSED);
  }

  private void assertBusinessException(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(expected);
  }
}
