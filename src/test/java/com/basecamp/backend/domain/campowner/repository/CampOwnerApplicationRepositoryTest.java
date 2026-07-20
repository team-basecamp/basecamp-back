package com.basecamp.backend.domain.campowner.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.basecamp.backend.common.config.JpaConfig;
import com.basecamp.backend.domain.camp.config.CampNamingStrategyConfig;
import com.basecamp.backend.domain.campowner.entity.ApplicationStatus;
import com.basecamp.backend.domain.campowner.entity.CampOwnerApplication;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.UserRepository;
import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V12~V15 의 DB 제약을 실제 MySQL 로 검증한다. 목킹으로는 확인할 수 없는 것들이다.
 *
 * <ul>
 *   <li>{@code uq_coa_user_pending} — 회원당 심사 중 신청 1건 (파생 컬럼 + UNIQUE, V12)
 *   <li>{@code version} — 동시 승인/반려 시 나중 커밋이 앞선 심사 이력을 덮어쓰지 못하게 하는 낙관적 락 (V13)
 *   <li>{@code chk_coa_status} — (V14)
 * </ul>
 *
 * <p>임베디드 DB 로 교체하지 않고 로컬 MySQL 을 그대로 쓴다(생성 컬럼과 CHECK 는 MySQL 동작이라 H2 로는 검증 불가). 설정 두 개를 직접 넣어주는
 * 이유는 {@code UserRepositoryTest} 와 같다.
 */
@DataJpaTest
@Import({JpaConfig.class, CampNamingStrategyConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CampOwnerApplicationRepositoryTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZONE);
  private static final Long ADMIN_A = 9L;
  private static final Long ADMIN_B = 10L;
  private static final String BIZ_NUMBER = "1234567890";

  @Autowired private CampOwnerApplicationRepository applicationRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private TestEntityManager em;

  @Autowired private PlatformTransactionManager transactionManager;

  /** FK(fk_coa_user) 가 걸려 있어 실재하는 회원이 필요하다. */
  private Long persistUser(String email) {
    return em.persistFlushFind(User.register("camper", email, null, Provider.KAKAO)).getId();
  }

  private CampOwnerApplication pending(Long userId, String businessNumber) {
    return CampOwnerApplication.submit(userId, businessNumber, "베이스캠프 오토캠핑장", "홍길동");
  }

  private int nativeUpdate(String sql, Object... params) {
    var query = em.getEntityManager().createNativeQuery(sql);
    for (int i = 0; i < params.length; i++) {
      query.setParameter(i + 1, params[i]);
    }
    return query.executeUpdate();
  }

  @Test
  @DisplayName("save_같은회원의심사중신청이둘_유니크제약으로막힌다")
  void save_같은회원의심사중신청이둘_유니크제약으로막힌다() {
    // given: uq_coa_user_pending 은 PENDING 인 행에만 걸린다(파생 컬럼이 NULL 이면 중복 허용).
    Long userId = persistUser("owner1@example.com");
    applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER));

    // when & then: 서비스의 existsBy 조회는 조회와 INSERT 사이의 경합을 막지 못한다. 최종 방어선은 여기다.
    assertThatThrownBy(() -> applicationRepository.saveAndFlush(pending(userId, "9876543210")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("save_반려후재신청_유니크제약에걸리지않는다")
  void save_반려후재신청_유니크제약에걸리지않는다() {
    // given: 반려된 행은 파생 컬럼이 NULL 이 되어 유니크 인덱스에서 빠진다.
    Long userId = persistUser("owner2@example.com");
    CampOwnerApplication rejected = applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER));
    rejected.reject(ADMIN_A, "서류 미비", CLOCK);
    applicationRepository.saveAndFlush(rejected);

    // when & then
    assertThatCode(() -> applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("save_신규신청_version이0으로초기화된다")
  void save_신규신청_version이0으로초기화된다() {
    // given
    Long userId = persistUser("owner3@example.com");
    CampOwnerApplication application = pending(userId, BIZ_NUMBER);

    // when
    CampOwnerApplication saved = applicationRepository.saveAndFlush(application);

    // then
    assertThat(saved.getVersion()).isZero();
  }

  /**
   * 관리자 A·B 가 각자 다른 트랜잭션에서 같은 신청을 읽고, B 가 먼저 커밋한 뒤 A 가 갱신하는 흐름을 재현한다.
   *
   * <p>클래스 레벨 {@code @Transactional} 을 끈다({@code NOT_SUPPORTED}). 하나의 트랜잭션 안에서는 두 작업자를 흉내 낼 수 없고,
   * 바깥 트랜잭션이 행 잠금을 쥔 채 새 트랜잭션이 같은 행을 건드리면 교착에 빠진다. 롤백이 없으므로 심은 데이터는 직접 지운다.
   */
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("approve_관리자B가먼저커밋_관리자A의승인이_낙관적락으로실패한다")
  void approve_선행커밋후_후속갱신이_낙관적락으로실패한다() {
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    Long userId = null;
    Long applicationId = null;
    try {
      // given: 신청이 하나 있고, 관리자 A 와 B 가 각자 그것을 읽어둔다(둘 다 version = 0 인 스냅샷).
      // 이 테스트만 롤백되지 않으므로(NOT_SUPPORTED) 이전 실행이 finally 전에 죽으면 행이 남는다.
      // 고정값을 쓰면 다음 실행이 유니크 제약에 걸리므로, 회원과 사업자번호를 실행마다 새로 만든다.
      //   - users.email          회원 유니크
      //   - uq_coa_biznum_approved  승인된 신청의 사업자번호 유니크 (이 테스트는 승인까지 진행한다)
      String email = "owner4+" + UUID.randomUUID() + "@example.com";
      String bizNumber = String.format("%010d", System.nanoTime() % 10_000_000_000L);
      userId =
          tx.execute(
              status ->
                  userRepository
                      .saveAndFlush(User.register("camper", email, null, Provider.KAKAO))
                      .getId());
      final Long uid = userId;
      applicationId =
          tx.execute(status -> applicationRepository.saveAndFlush(pending(uid, bizNumber)).getId());
      final Long aid = applicationId;

      CampOwnerApplication readByAdminA =
          tx.execute(status -> applicationRepository.findById(aid).orElseThrow());
      assertThat(readByAdminA.getVersion()).isZero();

      // when: 관리자 B 가 먼저 승인하고 커밋한다(version 0 -> 1).
      tx.executeWithoutResult(
          status -> applicationRepository.findById(aid).orElseThrow().approve(ADMIN_B, CLOCK));

      // then: 관리자 A 의 스냅샷은 아직 PENDING 이라 requirePending() 을 통과한다.
      //       엔티티 검사만으로는 못 막고, version 이 어긋난 UPDATE 가 0건이 되어 실패한다.
      readByAdminA.approve(ADMIN_A, CLOCK);
      assertThatThrownBy(
              () ->
                  tx.executeWithoutResult(
                      status -> applicationRepository.saveAndFlush(readByAdminA)))
          .isInstanceOf(ObjectOptimisticLockingFailureException.class);

      // B 의 심사 이력이 A 에게 덮이지 않았다.
      CampOwnerApplication persisted =
          tx.execute(status -> applicationRepository.findById(aid).orElseThrow());
      assertThat(persisted.getProcessedBy()).isEqualTo(ADMIN_B);
      assertThat(persisted.getVersion()).isEqualTo(1L);
    } finally {
      final Long aid = applicationId;
      final Long uid = userId;
      tx.executeWithoutResult(
          status -> {
            if (aid != null) {
              applicationRepository.deleteById(aid);
            }
            if (uid != null) {
              userRepository.deleteById(uid);
            }
          });
    }
  }

  @Test
  @DisplayName("update_허용되지않는status_CHECK제약으로막힌다")
  void update_허용되지않는status_CHECK제약으로막힌다() {
    // given: 애플리케이션은 @Enumerated(STRING) 이라 세 값만 쓴다. 이 제약이 막는 건 raw SQL·수동 패치다.
    Long userId = persistUser("owner5@example.com");
    Long applicationId = applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER)).getId();

    // when & then
    assertThatThrownBy(
            () ->
                nativeUpdate(
                    "UPDATE camp_owner_applications SET status = 'CANCELLED' WHERE application_id = ?",
                    applicationId))
        .isInstanceOf(PersistenceException.class)
        .hasMessageContaining("chk_coa_status");
  }

  @Test
  @DisplayName("update_소문자status_CHECK제약으로막힌다")
  void update_소문자status_CHECK제약으로막힌다() {
    // given: 테이블 collation 이 utf8mb4_unicode_ci 라 'pending' = 'PENDING' 이 참이다.
    //        V14 의 CHECK 는 이걸 통과시켰지만, @Enumerated(STRING) 조회는 Enum.valueOf() 라 대소문자를 구분한다.
    //        'pending' 이 저장되면 그 행을 읽는 순간 터진다. V15 에서 바이너리 collation 비교로 바꿔 막는다.
    Long userId = persistUser("owner6@example.com");
    Long applicationId = applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER)).getId();

    // when & then
    assertThatThrownBy(
            () ->
                nativeUpdate(
                    "UPDATE camp_owner_applications SET status = 'pending' WHERE application_id = ?",
                    applicationId))
        .isInstanceOf(PersistenceException.class)
        .hasMessageContaining("chk_coa_status");
  }

  @Test
  @DisplayName("findMyLatest_created_at이동일_PK타이브레이커로최신건을반환한다")
  void findMyLatest_createdAt이동일_PK타이브레이커로최신건을반환한다() {
    // given: created_at 은 DATETIME(소수점 초 없음)이라 반려 직후 재신청하면 두 행의 시각이 같아질 수 있다.
    //        테스트가 우연에 기대지 않도록 두 행의 created_at 을 명시적으로 같은 값으로 맞춘다.
    //        이때 createdAt 만으로 정렬하면 순서가 비결정적이라 옛 반려 건이 최신으로 잡힐 수 있다.
    Long userId = persistUser("owner7@example.com");
    CampOwnerApplication first = applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER));
    first.reject(ADMIN_A, "서류 미비", CLOCK);
    applicationRepository.saveAndFlush(first);
    CampOwnerApplication second = applicationRepository.saveAndFlush(pending(userId, BIZ_NUMBER));

    nativeUpdate(
        "UPDATE camp_owner_applications SET created_at = '2026-07-10 00:00:00' WHERE user_id = ?",
        userId);
    em.flush();
    em.clear();

    // when
    CampOwnerApplication found =
        applicationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId).orElseThrow();

    // then: 시각이 동률이면 단조 증가하는 PK 가 순서를 결정한다.
    assertThat(found.getId()).isEqualTo(second.getId());
    assertThat(found.getStatus()).isEqualTo(ApplicationStatus.PENDING);
  }
}
