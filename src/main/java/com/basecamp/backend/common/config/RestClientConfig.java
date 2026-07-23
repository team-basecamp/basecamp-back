package com.basecamp.backend.common.config;

import java.time.Duration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 소셜 로그인(카카오/구글/네이버) API 호출용 {@link RestClient} 빈.
 *
 * <p>외부 소셜 서버 지연이 우리 요청을 무한정 잡지 않도록 연결/읽기 타임아웃을 둔다.
 */
@Configuration
public class RestClientConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  @Bean
  public RestClient restClient(RestClient.Builder builder) {
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(CONNECT_TIMEOUT)
            .withReadTimeout(READ_TIMEOUT);
    return builder.requestFactory(ClientHttpRequestFactories.get(settings)).build();
  }
}
