package com.basecamp.backend.domain.weather.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OpenWeatherMap 5일/3시간 예보(/data/2.5/forecast) 응답 중 우리가 쓰는 필드만 매핑한다.
 *
 * <p>5일치를 3시간 간격으로 쪼개 최대 40개 항목이 {@code list} 에 담겨 온다. 항목 하나의 구조(main/weather)는 현재 날씨 응답과 같아 {@link
 * CurrentWeatherResponse} 의 것을 재사용한다.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ForecastResponse {

  /** 3시간 간격 예보 항목들. 응답에 city/cod/cnt 등도 오지만 쓰지 않으므로 무시된다. */
  private List<Item> list;

  @Getter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Item {

    /** 예보 시각(Unix 타임스탬프, 초). UTC 기준이므로 날짜로 바꿀 때 타임존을 적용해야 한다. */
    private Long dt;

    private CurrentWeatherResponse.Main main;

    private List<CurrentWeatherResponse.Weather> weather;
  }
}
