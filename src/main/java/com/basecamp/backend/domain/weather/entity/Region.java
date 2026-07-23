package com.basecamp.backend.domain.weather.entity;

import java.math.BigDecimal;
import lombok.Getter;

/**
 * 홈페이지 시/도별 날씨 위젯의 대표 지점 좌표.
 *
 * <p>행정구역은 바뀌지 않고 개수도 16개로 고정이므로 DB나 설정이 아닌 코드 상수로 둔다. (좌표는 각 시·도청 소재지 기준의 대표값이다. 시/도 단위 날씨라 수 km
 * 오차는 무의미하다.)
 */
@Getter
public enum Region {
  SEOUL("서울특별시", "126.9780", "37.5665"),
  BUSAN("부산광역시", "129.0756", "35.1796"),
  DAEGU("대구광역시", "128.6014", "35.8714"),
  INCHEON("인천광역시", "126.7052", "37.4563"),
  // 2026-07-01 광주광역시 + 전라남도 통합 출범(전남광주통합특별시 설치 특별법).
  // 두 광역단체가 폐지되고 단일 행정체제가 되어 항목을 하나로 합쳤다.
  // 대표 좌표는 옛 광주 도심(인구 중심)을 사용한다.
  JEONNAM_GWANGJU("전남광주통합특별시", "126.8526", "35.1595"),
  DAEJEON("대전광역시", "127.3845", "36.3504"),
  ULSAN("울산광역시", "129.3114", "35.5384"),
  SEJONG("세종특별자치시", "127.2890", "36.4800"),
  GYEONGGI("경기도", "127.0095", "37.2750"),
  GANGWON("강원특별자치도", "127.7298", "37.8228"),
  CHUNGBUK("충청북도", "127.4890", "36.6357"),
  CHUNGNAM("충청남도", "126.6728", "36.6588"),
  JEONBUK("전북특별자치도", "127.1088", "35.8203"),
  GYEONGBUK("경상북도", "128.7294", "36.5684"),
  GYEONGNAM("경상남도", "128.6811", "35.2383"),
  JEJU("제주특별자치도", "126.4983", "33.4890");

  /** 프론트에 그대로 노출되는 표시명 */
  private final String displayName;

  /** 경도(longitude). WeatherClient 의 lon 파라미터로 넘어간다. */
  private final BigDecimal mapX;

  /** 위도(latitude). WeatherClient 의 lat 파라미터로 넘어간다. */
  private final BigDecimal mapY;

  Region(String displayName, String mapX, String mapY) {
    this.displayName = displayName;
    // BigDecimal 은 double 이 아닌 String 으로 만든다(부동소수점 오차 방지).
    this.mapX = new BigDecimal(mapX);
    this.mapY = new BigDecimal(mapY);
  }
}
