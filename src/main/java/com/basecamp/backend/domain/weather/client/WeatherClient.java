package com.basecamp.backend.domain.weather.client;

// import 생략

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OpenWeatherMap API로 좌표의 날씨를 조회한다.
 *
 * <p>호출이 실패해도 예외를 던지지 않는다. 날씨는 캠핑장 조회를 막을 만큼 필수적인 정보가 아니며, 외부 API 장애가 상세페이지 전체를 죽이면 안 되기
 * 때문이다(KakaoGeocodingClient 와 동일한 fail-soft 정책).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherClient {

  // 현재 날씨 조회 엔드포인트 (홈페이지 시/도 날씨용)
  @Value("${openweather.current-url}")
  private String currentWeatherUrl;

  // 5일/3시간 예보 조회 엔드포인트 (캠핑장 상세 예약일 날씨용)
  @Value("${openweather.forecast-url}")
  private String forecastUrl;

  // 실제 HTTP 호출에 쓸 빈을 주입 (KakaoGeocodingClient와 동일)
  private final RestTemplate restTemplate;

  // ③ application-local.yml의 openweather.api-key를 주입받아봐
  @Value("${openweather.api-key}")
  private String apiKey;

  /**
   * 좌표의 현재 날씨를 조회한다. 실패 시 예외 대신 null 을 반환한다(fail-soft).
   *
   * @param mapX 경도(longitude) — 한국 기준 126~130
   * @param mapY 위도(latitude) — 한국 기준 33~38
   */
  public CurrentWeatherResponse getCurrentWeather(BigDecimal mapX, BigDecimal mapY) {
    // 지오코딩에 실패한 캠핑장은 좌표가 없다. 호출할 이유가 없으므로 조기 반환한다.
    if (mapX == null || mapY == null) {
      return null;
    }

    try {
      // ⚠️ lat = mapY(위도), lon = mapX(경도). 바꿔 넣으면 지구 반대편 날씨가 조용히 나온다.
      String url =
          UriComponentsBuilder.fromHttpUrl(currentWeatherUrl)
              .queryParam("lat", mapY)
              .queryParam("lon", mapX)
              .queryParam("appid", apiKey)
              .queryParam("units", "metric") // 없으면 켈빈(293.15) 으로 온다
              .queryParam("lang", "kr") // 없으면 "clear sky" 처럼 영어로 온다
              .build()
              .toUriString();

      return restTemplate.getForObject(url, CurrentWeatherResponse.class);

    } catch (RestClientException e) {
      // 날씨는 부가 정보다. 외부 API 장애가 캠핑장 조회를 막지 않도록 한다.
      // e.getMessage() 는 URL(=apiKey 포함)을 노출하므로 절대 로그에 남기지 않는다.
      log.warn("날씨 조회 실패. 좌표=({}, {}), 원인={}", mapX, mapY, e.getClass().getSimpleName());
      return null;
    }
  }

  /**
   * 좌표의 5일/3시간 예보를 조회한다. 실패 시 예외 대신 null 을 반환한다(fail-soft).
   *
   * @param mapX 경도(longitude), @param mapY 위도(latitude)
   */
  public ForecastResponse getForecast(BigDecimal mapX, BigDecimal mapY) {
    if (mapX == null || mapY == null) {
      return null;
    }

    try {
      // ⚠️ lat = mapY(위도), lon = mapX(경도).
      String url =
          UriComponentsBuilder.fromHttpUrl(forecastUrl)
              .queryParam("lat", mapY)
              .queryParam("lon", mapX)
              .queryParam("appid", apiKey)
              .queryParam("units", "metric")
              .queryParam("lang", "kr")
              .build()
              .toUriString();

      return restTemplate.getForObject(url, ForecastResponse.class);

    } catch (RestClientException e) {
      log.warn("날씨 예보 조회 실패. 좌표=({}, {}), 원인={}", mapX, mapY, e.getClass().getSimpleName());
      return null;
    }
  }
}
