package com.basecamp.backend.domain.weather.dto.response;

import com.basecamp.backend.domain.weather.client.CurrentWeatherResponse;
import com.basecamp.backend.domain.weather.entity.Region;
import com.basecamp.backend.domain.weather.entity.WeatherCondition;
import com.basecamp.backend.domain.weather.entity.WeatherStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * 홈페이지 시/도별 날씨 위젯 응답.
 *
 * <p>OpenWeatherMap 응답({@link CurrentWeatherResponse})을 그대로 내려주지 않고 이 DTO 로 변환한다. 외부 API 의
 * 구조(main.temp, weather[0].icon)를 프론트가 알게 되면, 나중에 날씨 API 를 교체할 때 프론트까지 함께 깨진다. 변환 계층을 두어 외부 계약 변경이
 * 우리 API 계약으로 새지 않게 막는다.
 *
 * <p>{@code @Jacksonized} 는 캐시(Redis) 에 저장된 JSON 을 다시 객체로 되살리기 위해 필요하다. {@code @Builder} 만 있으면
 * Jackson 이 이 객체를 만들 수단이 없어 역직렬화에 실패한다.
 */
@Getter
@Builder
@Jacksonized
public class RegionWeatherResponseDto {

  @Schema(
      description = "날씨 조회 상태. OK=정상, NO_DATA=예보 범위 밖(정상), FETCH_FAILED=외부 조회 실패",
      example = "OK")
  private WeatherStatus status;

  /** 시/도 표시명 (예: "전남광주통합특별시") */
  private String regionName;

  /** 기온(섭씨). units=metric 로 요청하므로 켈빈이 아니다. */
  private Double temp;

  /** 날씨 상태 (예: "흐림"). API 번역문이 아니라 {@link WeatherCondition} 이 코드로부터 결정한 문구다. */
  private String condition;

  /** 습도(%) */
  private Integer humidity;

  /** 날씨 아이콘 코드 (예: "04d"). 프론트가 이 코드로 아이콘 이미지를 매핑한다. */
  private String icon;

  /**
   * Region + 외부 응답 → 응답 DTO.
   *
   * <p>외부 응답의 어느 필드든 없을 수 있다고 가정한다. 날씨는 부가 정보이므로, 일부 값이 비어도 지역명만이라도 내려주는 편이 위젯 전체가 사라지는 것보다 낫다.
   */
  public static RegionWeatherResponseDto of(Region region, CurrentWeatherResponse response) {
    // WeatherClient 는 호출 실패 시 null 을 반환한다(fail-soft).
    if (response == null) {
      return RegionWeatherResponseDto.builder()
          .regionName(region.getDisplayName())
          .status(WeatherStatus.FETCH_FAILED)
          .build();
    }

    CurrentWeatherResponse.Main main = response.getMain();
    CurrentWeatherResponse.Weather weather = firstWeather(response);

    return RegionWeatherResponseDto.builder()
        .regionName(region.getDisplayName())
        .temp(main != null ? main.getTemp() : null)
        .humidity(main != null ? main.getHumidity() : null)
        .condition(describeCondition(weather))
        .icon(weather != null ? weather.getIcon() : null)
        .status(WeatherStatus.OK)
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

  /**
   * weather 배열의 대표값(첫 번째)을 꺼낸다. 없으면 null.
   *
   * <p>비가 오면서 안개가 끼는 식으로 요소가 여러 개일 수 있어 배열로 오지만, 위젯에는 대표 하나만 쓴다. 반대로 <b>배열이 비어 있거나 아예 없을 수도</b>
   * 있으므로 get(0) 을 그냥 호출하지 않는다.
   */
  private static CurrentWeatherResponse.Weather firstWeather(CurrentWeatherResponse response) {
    List<CurrentWeatherResponse.Weather> weather = response.getWeather();
    if (weather == null || weather.isEmpty()) {
      return null;
    }
    return weather.get(0);
  }
}
