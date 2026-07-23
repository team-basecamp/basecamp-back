package com.basecamp.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// @Async가 실제로 별도 스레드에서 실행되려면 AsyncAnnotationBeanPostProcessor가
// 등록돼 있어야 하는데, 이건 @EnableAsync를 붙여야 활성화된다.
@Configuration
@EnableAsync
public class AsyncConfig {}
