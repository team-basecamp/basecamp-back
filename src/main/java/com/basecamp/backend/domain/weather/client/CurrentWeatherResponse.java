package com.basecamp.backend.domain.weather.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OpenWeatherMap 현재 날씨(/data/2.5/weather) 응답 중 우리가 쓰는 필드만 매핑한다. 실제 응답에는 coord/wind/clouds/sys 등 더
 * 많은 필드가 있지만 전부 무시한다.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // 안쓰는 필드를 만들때 예외 던지는 것, 이걸 붙이면 조용히 무시한다.
public class CurrentWeatherResponse {

  private List<Weather> weather;

  private Main main;

  private Long dt;

  @Getter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Weather {
    /** OpenWeatherMap 날씨 코드(800=맑음, 500=약한 비 ...). 번역에 좌우되지 않아 이 값으로 문구를 결정한다. */
    private Integer id;

    /** API 가 번역한 설명("실 비" 등). id 로 문구를 만들지 못했을 때만 대체값으로 쓴다. */
    private String description;

    private String icon;
  }

  @Getter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Main {
    private Double temp; // 15.3 — 소수점이 있으므로 Double
    private Integer humidity; // 55 — 퍼센트 정수
  }
}
