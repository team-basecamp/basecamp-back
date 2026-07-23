package com.basecamp.backend.domain.weather.dto.response;

import com.basecamp.backend.domain.weather.client.CurrentWeatherResponse;
import com.basecamp.backend.domain.weather.client.ForecastResponse;
import com.basecamp.backend.domain.weather.entity.WeatherCondition;
import com.basecamp.backend.domain.weather.entity.WeatherStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * 캠핑장 상세 페이지의 예약일 날씨 응답.
 *
 * <p>프론트 계약(WeatherDay: date/temp/condition/humidity/icon)에 맞춘 형태다.
 */
@Getter
@Builder
@Jacksonized
public class CampWeatherResponseDto {

  @Schema(
      description = "날씨 조회 상태. OK=정상, NO_DATA=예보 범위 밖(정상), FETCH_FAILED=외부 조회 실패",
      example = "OK")
  private WeatherStatus status;

  private Long campId;
  private List<WeatherDay> weather;

  public static CampWeatherResponseDto of(
      Long campId, List<WeatherDay> weather, WeatherStatus status) {
    return CampWeatherResponseDto.builder().campId(campId).weather(weather).status(status).build();
  }

  /** 하루치 날씨. 3시간 간격 예보 여러 건을 하루 하나로 요약한 결과다. */
  @Getter
  @Builder
  @Jacksonized
  public static class WeatherDay {

    /** yyyy-MM-dd */
    private LocalDate date;

    private Double temp;

    /** 날씨 상태 (예: "흐림"). API 번역문이 아니라 {@link WeatherCondition} 이 코드로부터 결정한 문구다. */
    private String condition;

    private Integer humidity;

    private String icon;

    /** 그날의 대표 예보 항목 하나로 변환한다. */
    public static WeatherDay of(LocalDate date, ForecastResponse.Item item) {
      CurrentWeatherResponse.Main main = item.getMain();
      CurrentWeatherResponse.Weather weather = firstWeather(item);

      return WeatherDay.builder()
          .date(date)
          .temp(main != null ? main.getTemp() : null)
          .humidity(main != null ? main.getHumidity() : null)
          .condition(describeCondition(weather))
          .icon(weather != null ? weather.getIcon() : null)
          .build();
    }

    /**
     * 표시 문구는 코드({@code id})로 결정하고, 모르는 코드면 API 번역문으로 대체한다.
     *
     * <p>제공자가 새 코드를 추가해도 문구가 비어버리지 않도록 두 단계로 둔다.
     */
    private static String describeCondition(CurrentWeatherResponse.Weather weather) {
      if (weather == null) {
        return null;
      }
      String condition = WeatherCondition.describe(weather.getId());
      return condition != null ? condition : weather.getDescription();
    }

    /** weather 배열은 비어 있을 수 있으므로 get(0) 을 그냥 호출하지 않는다. */
    private static CurrentWeatherResponse.Weather firstWeather(ForecastResponse.Item item) {
      List<CurrentWeatherResponse.Weather> weather = item.getWeather();
      if (weather == null || weather.isEmpty()) {
        return null;
      }
      return weather.get(0);
    }
  }
}
