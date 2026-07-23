package com.basecamp.backend.common.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화. 엔티티의 {@code @CreatedDate}/{@code @LastModifiedDate} 자동 채움을 담당한다.
 *
 * <p>감사 시각과 도메인 시각을 모두 동일한 {@link Clock}(Asia/Seoul 고정)으로 산정해 서버 타임존 의존을 제거하고 테스트 시 시계 교체가 가능하도록
 * 한다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

  @Bean
  public Clock clock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }

  @Bean
  public DateTimeProvider auditingDateTimeProvider(Clock clock) {
    return () -> Optional.of(LocalDateTime.now(clock));
  }
}
