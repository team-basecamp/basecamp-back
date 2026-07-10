package com.basecamp.backend.domain.campowner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.campowner.dto.request.CampOwnerApplicationRequest;
import com.basecamp.backend.domain.campowner.dto.response.CampOwnerApplicationResponse;
import com.basecamp.backend.domain.campowner.entity.ApplicationStatus;
import com.basecamp.backend.domain.campowner.entity.CampOwnerApplication;
import com.basecamp.backend.domain.campowner.repository.CampOwnerApplicationRepository;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.UserRepository;

/**
 * 회원의 캠핑업체 전환 신청 단위 테스트(#53).
 */
@ExtendWith(MockitoExtension.class)
class CampOwnerApplicationServiceTest {

	private static final Long USER_ID = 7L;
	private static final String VALID_BIZ_NUMBER = "2208162517";
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

	@Mock
	private CampOwnerApplicationRepository applicationRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private CampOwnerApplicationService campOwnerApplicationService;

	private User activeUser() {
		return User.register("camper", "user@example.com", null, Provider.KAKAO);
	}

	private CampOwnerApplicationRequest request(String businessNumber) {
		return new CampOwnerApplicationRequest(businessNumber, "베이스캠프 오토캠핑장", "홍길동");
	}

	@Test
	@DisplayName("apply_정상_PENDING상태로저장하고_응답을반환한다")
	void apply_정상_PENDING상태로저장한다() {
		// given
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser()));
		given(applicationRepository.existsByUserIdAndStatus(USER_ID, ApplicationStatus.PENDING)).willReturn(false);
		given(applicationRepository.saveAndFlush(any(CampOwnerApplication.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		// when
		CampOwnerApplicationResponse response = campOwnerApplicationService.apply(USER_ID, request(VALID_BIZ_NUMBER));

		// then: 신청은 심사를 요청할 뿐 권한을 바꾸지 않는다.
		assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING.name());
		assertThat(response.userId()).isEqualTo(USER_ID);
		assertThat(response.businessNumber()).isEqualTo(VALID_BIZ_NUMBER);
		assertThat(response.processedAt()).isNull();
	}

	@Test
	@DisplayName("apply_이미캠핑업체_CO003을던진다")
	void apply_이미캠핑업체_CO003을던진다() {
		// given
		User owner = activeUser();
		owner.promoteToCampOwner();
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(owner));

		// when & then
		assertBusinessException(() -> campOwnerApplicationService.apply(USER_ID, request(VALID_BIZ_NUMBER)),
				ErrorCode.ALREADY_CAMP_OWNER);
		verify(applicationRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("apply_체크섬이틀린사업자번호_CO005를던진다")
	void apply_체크섬이틀린사업자번호_CO005를던진다() {
		// given: 형식(숫자 10자리)은 맞아 Bean Validation 은 통과하지만 체크섬이 틀리다.
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser()));

		// when & then
		assertBusinessException(() -> campOwnerApplicationService.apply(USER_ID, request("2208162518")),
				ErrorCode.INVALID_BUSINESS_NUMBER);
		verify(applicationRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("apply_이미심사중인신청이있음_CO002를던진다")
	void apply_이미심사중_CO002를던진다() {
		// given
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser()));
		given(applicationRepository.existsByUserIdAndStatus(USER_ID, ApplicationStatus.PENDING)).willReturn(true);

		// when & then
		assertBusinessException(() -> campOwnerApplicationService.apply(USER_ID, request(VALID_BIZ_NUMBER)),
				ErrorCode.CAMP_OWNER_APPLICATION_ALREADY_PENDING);
		verify(applicationRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("apply_동시신청으로유니크위반_CO002를던진다")
	void apply_동시신청으로유니크위반_CO002를던진다() {
		// given: 조회와 INSERT 사이에 다른 요청이 먼저 PENDING 을 넣었다(uq_coa_user_pending 위반).
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser()));
		given(applicationRepository.existsByUserIdAndStatus(USER_ID, ApplicationStatus.PENDING)).willReturn(false);
		given(applicationRepository.saveAndFlush(any(CampOwnerApplication.class)))
				.willThrow(new DataIntegrityViolationException("duplicate user_id_pending"));

		// when & then: 500 이 아니라 "이미 심사 중"으로 응답해야 한다.
		assertBusinessException(() -> campOwnerApplicationService.apply(USER_ID, request(VALID_BIZ_NUMBER)),
				ErrorCode.CAMP_OWNER_APPLICATION_ALREADY_PENDING);
	}

	@Test
	@DisplayName("apply_탈퇴한회원_U002를던진다")
	void apply_탈퇴한회원_U002를던진다() {
		// given
		User withdrawn = activeUser();
		withdrawn.withdraw("사유", Clock.fixed(NOW, ZONE));
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(withdrawn));

		// when & then
		assertBusinessException(() -> campOwnerApplicationService.apply(USER_ID, request(VALID_BIZ_NUMBER)),
				ErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("apply_제재된회원_A007을던진다")
	void apply_제재된회원_A007을던진다() {
		// given: 제재 중에는 access 토큰이 인증 필터에서 거부되지만, 서비스에서도 최종 방어한다.
		User blacklisted = activeUser();
		blacklisted.blacklist("어뷰징", Clock.fixed(NOW, ZONE));
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(blacklisted));

		// when & then
		assertBusinessException(() -> campOwnerApplicationService.apply(USER_ID, request(VALID_BIZ_NUMBER)),
				ErrorCode.BLACKLISTED_USER);
	}

	@Test
	@DisplayName("apply_존재하지않는회원_U002를던진다")
	void apply_존재하지않는회원_U002를던진다() {
		// given
		given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

		// when & then
		assertBusinessException(() -> campOwnerApplicationService.apply(USER_ID, request(VALID_BIZ_NUMBER)),
				ErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("findMyLatestApplication_최신신청_반환한다")
	void findMyLatestApplication_최신신청_반환한다() {
		// given: 반려 후 재신청하면 여러 건이 쌓이므로 가장 최근 것을 본다.
		CampOwnerApplication application = CampOwnerApplication.submit(USER_ID, VALID_BIZ_NUMBER, "상호", "홍길동");
		ReflectionTestUtils.setField(application, "id", 3L);
		given(applicationRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
				.willReturn(Optional.of(application));

		// when
		CampOwnerApplicationResponse response = campOwnerApplicationService.findMyLatestApplication(USER_ID);

		// then
		assertThat(response.applicationId()).isEqualTo(3L);
		assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING.name());
	}

	@Test
	@DisplayName("findMyLatestApplication_신청이력없음_CO001을던진다")
	void findMyLatestApplication_신청이력없음_CO001을던진다() {
		// given
		given(applicationRepository.findFirstByUserIdOrderByCreatedAtDesc(anyLong())).willReturn(Optional.empty());

		// when & then
		assertBusinessException(() -> campOwnerApplicationService.findMyLatestApplication(USER_ID),
				ErrorCode.CAMP_OWNER_APPLICATION_NOT_FOUND);
	}

	private void assertBusinessException(Runnable action, ErrorCode expected) {
		assertThatThrownBy(action::run)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(expected);
	}

}
