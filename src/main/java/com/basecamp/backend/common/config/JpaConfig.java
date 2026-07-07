package com.basecamp.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화. 엔티티의 {@code @CreatedDate}/{@code @LastModifiedDate} 자동 채움을 담당한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
