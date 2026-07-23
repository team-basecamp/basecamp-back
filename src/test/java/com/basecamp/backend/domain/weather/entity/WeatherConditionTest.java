package com.basecamp.backend.domain.weather.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** OpenWeatherMap 날씨 코드 → 한국어 문구 변환 단위 테스트. */
class WeatherConditionTest {

  @Test
  @DisplayName("describe_id없음_null을반환한다")
  void describe_id없음_null을반환한다() {
    // given & when & then
    assertThat(WeatherCondition.describe(null)).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "741, 안개",
    "751, 황사",
    "761, 황사",
    "771, 돌풍",
    "781, 토네이도",
  })
  @DisplayName("describe_7xx대기코드_현상별로다른문구를반환한다")
  void describe_7xx대기코드_현상별로다른문구를반환한다(int id, String expected) {
    // given & when & then: 7xx 는 대역 안에 서로 다른 현상이 섞여 있어 코드별로 갈라야 한다
    assertThat(WeatherCondition.describe(id)).isEqualTo(expected);
  }

  @Test
  @DisplayName("describe_토네이도_안개로표시되지않는다")
  void describe_토네이도_안개로표시되지않는다() {
    // given & when & then: 7xx 를 대역째 "안개"로 뭉치면 토네이도가 안개가 되어 야외 위험을 잘못 안내한다
    assertThat(WeatherCondition.describe(781)).isNotEqualTo("안개");
  }

  @ParameterizedTest
  @ValueSource(ints = {799, 900})
  @DisplayName("describe_모르는코드_null을반환해API번역문에맡긴다")
  void describe_모르는코드_null을반환한다(int id) {
    // given & when & then
    assertThat(WeatherCondition.describe(id)).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "500, 약한 비",
    "602, 많은 눈",
    "800, 맑음",
    "804, 흐림",
  })
  @DisplayName("describe_기존대역_문구가바뀌지않는다")
  void describe_기존대역_문구가바뀌지않는다(int id, String expected) {
    // given & when & then: 7xx 를 고치면서 다른 대역이 함께 바뀌지 않았는지 확인한다
    assertThat(WeatherCondition.describe(id)).isEqualTo(expected);
  }
}
