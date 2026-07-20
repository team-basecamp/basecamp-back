package com.basecamp.backend.domain.camp.config;

import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.camp.service.CampService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

@Configuration
@ConditionalOnProperty(
    name = "camp.data.init.enabled",
    havingValue = "true",
    matchIfMissing = true // 설정 없으면 true로 기본값
    )
public class CampDataInitializer {

  private static final Logger logger = LoggerFactory.getLogger(CampDataInitializer.class);

  private final CampService campService;
  private final CampRepository campRepository;

  public CampDataInitializer(CampService campService, CampRepository campRepository) {
    this.campService = campService;
    this.campRepository = campRepository;
  }

  /**
   * 앱이 완전히 준비된 후에 별도 스레드에서 데이터 동기화 실행 - ApplicationReadyEvent: 앱 시작이 완료되면 발생하는 이벤트 - @Async: 별도
   * 스레드에서 비동기 실행 (메인 스레드 블로킹 안 함) - camp.data.init.enabled=false면 이 빈 자체가 등록되지 않아 동기화가 실행되지 않음
   */
  @EventListener(ApplicationReadyEvent.class)
  @Async // 비동기 실행 (별도 스레드)
  public void initGocampingData() {
    try {
      // 이미 데이터가 있으면 건너뛰기
      if (campRepository.count() > 0) {
        logger.info("📦 camps 테이블에 이미 {}개의 데이터가 있어 초기 동기화를 건너뜁니다.", campRepository.count());
        return;
      }

      logger.info("고캠핑 API에서 캠프 데이터 동기화 시작...");
      campService.fetchAndSaveCampsFromGocampingApi();
      logger.info(" 고캠핑 API 데이터 동기화 완료!");

    } catch (Exception e) {
      logger.error(" 고캠핑 데이터 동기화 실패 (앱은 정상 구동됩니다): {}", e.getMessage(), e);
      // 예외를 로깅하지만 앱 구동을 막지 않음
    }
  }
}
