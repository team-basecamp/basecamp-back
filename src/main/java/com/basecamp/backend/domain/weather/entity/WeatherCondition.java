package com.basecamp.backend.domain.weather.entity;

/**
 * OpenWeatherMap 날씨 코드({@code weather[0].id})를 우리 서비스의 한국어 문구로 옮긴다.
 *
 * <p>API 가 {@code lang=kr} 로 내려주는 번역문은 "실 비"(light rain), "온흐림"(overcast clouds) 처럼 어색한 것이 섞여 있고,
 * 제공자가 번역을 바꾸면 우리 화면 문구도 함께 바뀐다. 반면 <b>코드는 계약이라 잘 바뀌지 않는다.</b> 그래서 표시 문구는 우리가 코드로부터 직접 결정한다.
 *
 * <p>코드 대역: 2xx 뇌우 / 3xx 이슬비 / 5xx 비 / 6xx 눈 / 7xx 대기(안개·황사·돌풍) / 800 맑음 / 80x 구름
 */
public final class WeatherCondition {

  private WeatherCondition() {
    // 상태 없는 유틸리티 클래스. 인스턴스를 만들 이유가 없다.
  }

  /**
   * 날씨 코드를 한국어 문구로 옮긴다.
   *
   * @return 모르는 코드거나 id 가 없으면 {@code null}(호출자가 API 번역문으로 대체한다)
   */
  public static String describe(Integer id) {
    if (id == null) {
      return null;
    }
    // 100 으로 나눠 대역(2xx/5xx/8xx...)부터 가른 뒤, 필요한 곳만 세부 코드를 본다.
    return switch (id / 100) {
      case 2 -> "뇌우";
      case 3 -> "이슬비";
      case 5 -> rain(id);
      case 6 -> snow(id);
      case 7 -> atmosphere(id);
      case 8 -> clouds(id);
      default -> null;
    };
  }

  private static String rain(int id) {
    if (id == 500) return "약한 비";
    if (id >= 502 && id <= 504) return "강한 비";
    if (id == 511) return "어는 비";
    if (id >= 520) return "소나기";
    return "비";
  }

  private static String snow(int id) {
    if (id == 600) return "약한 눈";
    if (id == 602) return "많은 눈";
    if (id >= 611 && id <= 616) return "진눈깨비";
    if (id >= 620) return "소낙눈";
    return "눈";
  }

  /**
   * 7xx 는 다른 대역과 달리 안에 서로 다른 현상이 섞여 있어(박무·황사·돌풍·토네이도) 뭉뚱그릴 수 없다. 실제 안개는 741 하나뿐이라, 대역 전체를 "안개"로 두면
   * 돌풍(771)·토네이도(781)까지 안개가 되어 야외 활동인 캠핑의 위험을 잘못 안내하게 된다.
   *
   * <p>모르는 코드에 임의의 문구를 붙이지 않고 null 을 돌려 API 번역문에 맡긴다(다른 대역과 다른 점).
   */
  private static String atmosphere(int id) {
    return switch (id) {
      case 701 -> "박무";
      case 711 -> "연기";
      case 721 -> "연무";
      case 731 -> "모래먼지";
      case 741 -> "안개";
      case 751, 761 -> "황사";
      case 762 -> "화산재";
      case 771 -> "돌풍";
      case 781 -> "토네이도";
      default -> null;
    };
  }

  private static String clouds(int id) {
    if (id == 800) return "맑음";
    if (id == 801) return "구름 조금";
    if (id == 802) return "구름 많음";
    return "흐림"; // 803, 804
  }
}
