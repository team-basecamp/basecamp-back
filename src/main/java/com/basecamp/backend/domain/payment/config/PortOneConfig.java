package com.basecamp.backend.domain.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 포트원 설정 바인딩 활성화. {@link PortOneProperties} 를 빈으로 등록한다.
 *
 * <p>{@code SecurityConfig} 의 {@code @EnableConfigurationProperties} 목록에 얹지 않고 여기 따로 두는 이유는, 결제 설정을
 * 아는 곳을 payment 도메인 안으로 묶기 위해서다.
 */
@Configuration
@EnableConfigurationProperties(PortOneProperties.class)
public class PortOneConfig {}
