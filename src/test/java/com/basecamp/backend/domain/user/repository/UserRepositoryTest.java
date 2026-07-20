package com.basecamp.backend.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.basecamp.backend.common.config.JpaConfig;
import com.basecamp.backend.domain.camp.config.CampNamingStrategyConfig;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.domain.user.entity.User;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V9 의 "활성 회원에게만 email 유니크" 제약을 실제 DB 로 검증한다.
 *
 * <p>파생 컬럼 {@code email_active}(= 탈퇴 시 NULL)에 유니크 인덱스가 걸려 있어, MySQL 이 NULL 을 중복으로 보지 않는 성질 덕에 탈퇴
 * 회원은 같은 email 을 몇 개든 보유할 수 있다. 이 동작은 목킹으로 검증할 수 없으므로 리포지토리 테스트로 확인한다.
 *
 * <p>임베디드 DB 로 교체하지 않고 로컬 MySQL 을 그대로 쓴다(생성 컬럼은 MySQL 고유 기능이라 H2 로는 검증 불가). {@code @DataJpaTest} 는
 * 기본적으로 각 테스트를 롤백한다.
 *
 * <p>{@code @DataJpaTest} 는 일반 {@code @Configuration} 을 로드하지 않으므로 두 설정을 직접 넣어준다. {@link JpaConfig}
 * 없이는 auditing 이 꺼져 {@code created_at} 이 채워지지 않고, {@link CampNamingStrategyConfig} 없이는 네이밍 전략이 달라져
 * {@code ddl-auto: validate} 가 실패한다.
 */
@DataJpaTest
@Import({JpaConfig.class, CampNamingStrategyConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

  private static final String EMAIL = "v9-unique-test@example.com";

  @Autowired private UserRepository userRepository;

  @Autowired private Clock clock;

  private User newUser(Provider provider) {
    return User.register("camper", EMAIL, null, provider);
  }

  @Test
  @DisplayName("save_활성회원이_같은이메일로_둘_유니크제약에걸린다")
  void save_활성회원이_같은이메일로_둘_유니크제약에걸린다() {
    // given
    userRepository.saveAndFlush(newUser(Provider.KAKAO));

    // when & then: 활성 회원끼리는 여전히 email 중복이 불가해야 한다.
    // (타 provider 가입 차단과 동시 가입 경합의 최종 방어선이 이 제약이다)
    assertThatThrownBy(() -> userRepository.saveAndFlush(newUser(Provider.NAVER)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("save_탈퇴회원과_같은이메일로_재가입할수있다")
  void save_탈퇴회원과_같은이메일로_재가입할수있다() {
    // given: 가입 후 탈퇴(soft delete). 옛 행은 email 을 그대로 들고 남는다.
    User withdrawn = userRepository.saveAndFlush(newUser(Provider.KAKAO));
    withdrawn.withdraw("사유", clock);
    userRepository.saveAndFlush(withdrawn);

    // when & then: 탈퇴 행의 email_active 는 NULL 이므로 유니크 인덱스에 걸리지 않는다.
    assertThatCode(() -> userRepository.saveAndFlush(newUser(Provider.NAVER)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("findByEmailAndDeletedAtIsNull_탈퇴회원은_조회되지않는다")
  void findByEmailAndDeletedAtIsNull_탈퇴회원은_조회되지않는다() {
    // given
    User withdrawn = userRepository.saveAndFlush(newUser(Provider.KAKAO));
    withdrawn.withdraw("사유", clock);
    userRepository.saveAndFlush(withdrawn);

    // when & then: 조회되지 않아야 로그인 시 register 경로를 타 재가입된다.
    assertThat(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).isEmpty();
  }

  @Test
  @DisplayName("findByEmailAndDeletedAtIsNull_재가입후_새회원만_조회된다")
  void findByEmailAndDeletedAtIsNull_재가입후_새회원만_조회된다() {
    // given
    User withdrawn = userRepository.saveAndFlush(newUser(Provider.KAKAO));
    withdrawn.withdraw("사유", clock);
    userRepository.saveAndFlush(withdrawn);
    User rejoined = userRepository.saveAndFlush(newUser(Provider.NAVER));

    // when & then: 같은 email 을 가진 행이 둘이지만 활성 회원은 새로 가입한 쪽 하나뿐이다.
    assertThat(userRepository.findByEmailAndDeletedAtIsNull(EMAIL))
        .get()
        .extracting(User::getId, User::getProvider)
        .containsExactly(rejoined.getId(), Provider.NAVER);
    assertThat(rejoined.getId()).isNotEqualTo(withdrawn.getId());
  }
}
