package com.basecamp.backend.domain.camp.config;

import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// camps 테이블의 lineIntro/doNm/sbrsCl/toiletCo 등 camelCase 컬럼명을 Hibernate가
// snake_case로 변환하지 않고 그대로 사용하도록 물리 네이밍 전략을 표준(무변환)으로 지정한다.
// 다른 모든 엔티티는 이미 @Column(name=...)에 snake_case를 명시하고 있어 영향 없음.
@Configuration
public class CampNamingStrategyConfig {

  @Bean
  public PhysicalNamingStrategy physicalNamingStrategy() {
    return PhysicalNamingStrategyStandardImpl.INSTANCE;
  }
}
