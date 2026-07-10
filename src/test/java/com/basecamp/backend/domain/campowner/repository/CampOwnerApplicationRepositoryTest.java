package com.basecamp.backend.domain.campowner.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.basecamp.backend.common.config.JpaConfig;
import com.basecamp.backend.domain.camp.config.CampNamingStrategyConfig;
import com.basecamp.backend.domain.campowner.entity.ApplicationStatus;
import com.basecamp.backend.domain.campowner.entity.CampOwnerApplication;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.domain.user.entity.User;

import jakarta.persistence.PersistenceException;

/**
 * V12~V14 의 DB 제약을 실제 MySQL 로 검증한다. 목킹으로는 확인할 수 없는 것들이다.
 *
 * <ul>
 *   <li>{@code uq_coa_user_pending} — 회원당 심사 중 신청 1건 (파생 컬럼 + UNIQUE, V12)</li>
 *   <li>{@code version} — 동시 승인/반려 시 나중 커밋이 앞선 심사 이력을 덮어쓰지 못하게 하는 낙관적 락 (V13)</li>
 *   <li>{@code chk_coa_status} — status 허용값 강제 (V14)</li>
 * </ul>
 *
 * <p>임베디드 DB 로 교체하지 않고 로컬 MySQL 을 그대로 쓴다(생성 컬럼과 CHECK 는 MySQL 동작이라 H2 로는 검증 불가).
 * 설정 두 개를 직접 넣어주는 이유는 {@code UserRepositoryTest} 와 같다.</p>
 */
@DataJpaTest
@Import({JpaConfig.class, CampNamingStrategyConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CampOwnerApplicationRepositoryTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZONE);
	private static final Long ADMIN_ID = 9L;
	private static final String BIZ_NUMBER = "2208162517";

	@Autowired
	private CampOwnerApplicationRepository applicationRepository;

	@Autowired
	private TestEntityManager em;

	/** FK(fk_coa_user) 가 걸려 있어 실재하는 회원이 필요하다. */
	private Long persistUser(String email) {
		return em.persistFlushFind(User.register("camper", email, null, Provider.KAKAO)).getId();
	}

	private CampOwnerApplication pending(Long userId, String businessNumber) {
		return CampOwnerApplication.submit(userId, businessNumber, "베이스캠프 오토캠핑장", "홍길동");
	}

	@Test
	@DisplayName("save_같은회원의심사중신청이둘_유니크제약으로막힌다")
	void save_같은회원의심사중신청이둘_유니크제약으로막힌다() {
		// given: uq_coa_user_pending 은 PENDING 인 행에만 걸린다(파생 컬럼이 NULL 이면 중복 허용).
		Long userId = persistUser("owner1@example.com");
		applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER));

		// when & then: 서비스의 existsBy 조회는 조회와 INSERT 사이의 경합을 막지 못한다. 최종 방어선은 여기다.
		assertThatThrownBy(() -> applicationRepository.saveAndFlush(pending(userId, "1208147521")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("save_반려후재신청_유니크제약에걸리지않는다")
	void save_반려후재신청_유니크제약에걸리지않는다() {
		// given: 반려된 행은 파생 컬럼이 NULL 이 되어 유니크 인덱스에서 빠진다.
		Long userId = persistUser("owner2@example.com");
		CampOwnerApplication rejected = applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER));
		rejected.reject(ADMIN_ID, "서류 미비", CLOCK);
		applicationRepository.saveAndFlush(rejected);

		// when & then
		assertThatCode(() -> applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER)))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("save_신규신청_version이0으로초기화된다")
	void save_신규신청_version이0으로초기화된다() {
		// given & when
		Long userId = persistUser("owner3@example.com");
		CampOwnerApplication saved = applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER));

		// then
		assertThat(saved.getVersion()).isZero();
	}

	@Test
	@DisplayName("approve_동시처리로version이어긋남_낙관적락으로두번째갱신이실패한다")
	void approve_동시처리로version이어긋남_두번째갱신이실패한다() {
		// given: 관리자 A 가 신청을 읽어둔다.
		Long userId = persistUser("owner4@example.com");
		CampOwnerApplication application = applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER));

		// 관리자 B 가 먼저 처리해 커밋한 상황을 재현한다.
		// 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로 관리자 A 가 들고 있는 엔티티의 version 은 옛 값 그대로다.
		em.getEntityManager()
				.createQuery("update CampOwnerApplication a set a.version = a.version + 1 where a.id = :id")
				.setParameter("id", application.getId())
				.executeUpdate();

		// when: 관리자 A 가 승인한다. requirePending() 은 통과한다 — 그의 스냅샷은 아직 PENDING 이다.
		application.approve(ADMIN_ID, CLOCK);

		// then: version 이 어긋나 UPDATE 가 0건이 되고 실패한다. GlobalExceptionHandler 가 C005(409)로 변환한다.
		assertThatThrownBy(() -> applicationRepository.saveAndFlush(application))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	@Test
	@DisplayName("update_허용되지않는status_CHECK제약으로막힌다")
	void update_허용되지않는status_CHECK제약으로막힌다() {
		// given: 애플리케이션은 @Enumerated(STRING) 이라 세 값만 쓴다. 이 제약이 막는 건 raw SQL·수동 패치다.
		Long userId = persistUser("owner5@example.com");
		Long applicationId = applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER)).getId();

		// when & then: 잘못된 값이 들어가면 조회 시 enum 변환이 터져 관리자 목록 API 가 통째로 죽는다.
		assertThatThrownBy(() -> em.getEntityManager()
				.createNativeQuery("UPDATE camp_owner_applications SET status = 'CANCELLED' WHERE application_id = ?")
				.setParameter(1, applicationId)
				.executeUpdate())
				.isInstanceOf(PersistenceException.class)
				.hasMessageContaining("chk_coa_status");
	}

	@Test
	@DisplayName("update_소문자status_대소문자무시collation이라_CHECK를통과한다")
	void update_소문자status_CHECK를통과한다() {
		// given: 테이블 collation 이 utf8mb4_unicode_ci(대소문자 무시)라 'pending' = 'PENDING' 이 참이다.
		//        즉 소문자로는 애초에 파생 컬럼 UNIQUE 를 우회할 수 없다. CHECK 도 같은 이유로 통과한다.
		Long userId = persistUser("owner6@example.com");
		Long applicationId = applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER)).getId();

		// when
		em.getEntityManager()
				.createNativeQuery("UPDATE camp_owner_applications SET status = 'pending' WHERE application_id = ?")
				.setParameter(1, applicationId)
				.executeUpdate();
		em.clear();

		// then: 파생 컬럼도 여전히 채워져 있어 "회원당 심사 중 1건" 규칙이 유지된다.
		assertThatThrownBy(() -> applicationRepository.saveAndFlush(pending(userId, "1208147521")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("findMyLatest_같은초에반려후재신청_PK타이브레이커로최신건을반환한다")
	void findMyLatest_같은초에반려후재신청_최신건을반환한다() {
		// given: created_at 은 DATETIME(소수점 초 없음)이라 두 신청이 같은 초에 만들어지면 정렬이 결정되지 않는다.
		//        createdAt 만으로 정렬하면 옛 반려 건이 최신으로 잡혀 /me 가 잘못된 상태를 보여준다.
		Long userId = persistUser("owner7@example.com");
		CampOwnerApplication first = applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER));
		first.reject(ADMIN_ID, "서류 미비", CLOCK);
		applicationRepository.saveAndFlush(first);
		CampOwnerApplication second = applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER));

		// when
		CampOwnerApplication found =
				applicationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId).orElseThrow();

		// then: 단조 증가하는 PK 가 동률을 깬다.
		assertThat(found.getId()).isEqualTo(second.getId());
		assertThat(found.getStatus()).isEqualTo(ApplicationStatus.PENDING);
	}

}
