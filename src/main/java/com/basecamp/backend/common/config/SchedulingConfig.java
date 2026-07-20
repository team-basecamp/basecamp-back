package com.basecamp.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 활성화.
 *
 * <p>{@link EnableScheduling} 이 있어야 {@code @Scheduled} 메서드가 실제로 주기 실행된다. 켜지 않으면 스케줄러가 <b>예외 없이 조용히
 * 동작하지 않는다</b>. 예약 D-1 알림({@code ReservationReminderScheduler})과 기존 예약 자동 반려({@code
 * ReservationExpiryScheduler})가 이 설정에 의존한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
