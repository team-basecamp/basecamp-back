package com.basecamp.backend.domain.weather.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.service.CampService;
import com.basecamp.backend.domain.weather.client.CurrentWeatherResponse;
import com.basecamp.backend.domain.weather.client.ForecastResponse;
import com.basecamp.backend.domain.weather.client.WeatherCache;
import com.basecamp.backend.domain.weather.client.WeatherClient;
import com.basecamp.backend.domain.weather.dto.response.CampWeatherResponseDto;
import com.basecamp.backend.domain.weather.dto.response.RegionWeatherResponseDto;
import com.basecamp.backend.domain.weather.entity.Region;
import com.basecamp.backend.domain.weather.entity.WeatherStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 날씨 조회 비즈니스 로직.
 *
 * <p>DB 를 쓰지 않으므로 {@code @Transactional} 이 없다. 외부 HTTP 호출과 캐시 조회뿐이라 트랜잭션을 열면 커넥션만
 * 붙잡는다(CampService.registerCamp 가 지오코딩 때문에 트랜잭션을 두지 않는 것과 같은 이유다).
 */
@Service
@RequiredArgsConstructor
public class WeatherService {

  private final WeatherClient weatherClient;
  private final WeatherCache weatherCache;

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final int REPRESENTATIVE_HOUR = 12; // 그날의 대표 시각(정오)
  private final CampService campService;

  /**
   * 전국 시/도의 현재 날씨를 조회한다(홈페이지 위젯용).
   *
   * <p>지역 하나가 실패해도 나머지는 그대로 내려간다. 실패한 지역은 값이 null 인 채로 포함되며, 프론트가 "정보 없음"으로 표시한다. 16개 중 하나 때문에 위젯
   * 전체가 사라지면 안 된다.
   */
  public List<RegionWeatherResponseDto> getRegionWeathers() {
    return Arrays.stream(Region.values()).map(this::getRegionWeather).toList();
  }

  private RegionWeatherResponseDto getRegionWeather(Region region) {
    // 1) 캐시 먼저. 있으면 외부 호출 없이 끝난다.
    RegionWeatherResponseDto cached = weatherCache.get(region);
    if (cached != null) {
      return cached;
    }

    // 2) 캐시 미스 → 외부 API 호출 (실패 시 response 는 null)
    CurrentWeatherResponse response =
        weatherClient.getCurrentWeather(region.getMapX(), region.getMapY());
    RegionWeatherResponseDto dto = RegionWeatherResponseDto.of(region, response);

    // 3) 성공한 결과만 캐시한다. 실패 응답까지 캐시하면 장애가 TTL 동안 고정되어,
    //    외부 API 가 복구돼도 캐시가 만료될 때까지 "정보 없음"이 계속 나간다.
    if (dto.getTemp() != null) {
      weatherCache.put(region, dto);
    }
    return dto;
  }

  /**
   * 캠핑장의 예약 기간 날씨를 조회한다.
   *
   * <p>예보 범위(5일) 밖의 날짜는 결과에 담기지 않는다. "먼 미래라 예보가 없음"은 오류가 아니므로 예외를 던지지 않고 그 날짜를 생략한다. 좌표가 없는
   * 캠핑장(지오코딩 실패)도 빈 리스트다.
   */
  public CampWeatherResponseDto getCampWeather(
      Long campId, LocalDate checkInDate, LocalDate checkOutDate) {
    if (checkInDate == null || checkOutDate == null || checkInDate.isAfter(checkOutDate)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "체크인 날짜는 체크아웃 날짜보다 늦을 수 없습니다.");
    }

    // 존재하지 않는 캠핑장이면 CAMP_NOT_FOUND(404). 조회 규칙은 CampService 가 이미 갖고 있으므로 재사용한다.
    Camp camp = campService.getCampId(campId);

    List<CampWeatherResponseDto.WeatherDay> forecast = getForecast(camp);

    // 요청 기간에 걸치는 날만 남긴다. 5일치를 통째로 캐시해두고 여기서 자른다.
    List<CampWeatherResponseDto.WeatherDay> filtered =
        forecast.stream()
            .filter(
                day -> !day.getDate().isBefore(checkInDate) && !day.getDate().isAfter(checkOutDate))
            .toList();
    WeatherStatus status = resolveStatus(forecast, filtered);

    return CampWeatherResponseDto.of(campId, filtered, status);
  }

  /**
   * 예보 조회 결과로 날씨 상태를 판단한다.
   *
   * <ul>
   *   <li>예보 자체를 못 받음(외부 실패 or 좌표 없음) → FETCH_FAILED
   *   <li>예보는 받았으나 요청 기간에 걸치는 날이 없음(먼 미래 등) → NO_DATA
   *   <li>요청 기간에 예보가 있음 → OK
   * </ul>
   */
  private WeatherStatus resolveStatus(
      List<CampWeatherResponseDto.WeatherDay> forecast,
      List<CampWeatherResponseDto.WeatherDay> filtered) {

    if (forecast.isEmpty()) {
      return WeatherStatus.FETCH_FAILED;
    }
    if (filtered.isEmpty()) {
      return WeatherStatus.NO_DATA;
    }
    return WeatherStatus.OK;
  }

  /** 캠핑장의 5일 예보를 얻는다(캐시 우선). 조회 불가 시 빈 리스트. */
  private List<CampWeatherResponseDto.WeatherDay> getForecast(Camp camp) {
    List<CampWeatherResponseDto.WeatherDay> cached = weatherCache.getCampForecast(camp.getCampId());
    if (cached != null) {
      return cached;
    }

    ForecastResponse response = weatherClient.getForecast(camp.getMapX(), camp.getMapY());
    List<CampWeatherResponseDto.WeatherDay> forecast = toDailyForecast(response);

    // 실패(빈 결과)는 캐시하지 않는다. 외부 API 가 복구돼도 TTL 동안 빈 응답이 고정된다.
    if (!forecast.isEmpty()) {
      weatherCache.putCampForecast(camp.getCampId(), forecast);
    }
    return forecast;
  }

  /**
   * 3시간 간격 40개 항목을 날짜별 하루 1건으로 요약한다.
   *
   * <p>기온은 평균 낼 수 있어도 날씨 상태("맑음"/"비")와 아이콘은 평균이 불가능하다. 그래서 하루의 <b>대표 시각(정오에 가장 가까운 항목)</b> 하나를 통째로
   * 골라 쓴다. 캠핑은 낮 시간대가 중심이므로 새벽보다 정오가 그날을 더 잘 대표한다.
   */
  private List<CampWeatherResponseDto.WeatherDay> toDailyForecast(ForecastResponse response) {
    if (response == null || response.getList() == null || response.getList().isEmpty()) {
      return List.of();
    }

    return response.getList().stream()
        .filter(item -> item.getDt() != null)
        // 같은 날짜끼리 묶는다. dt 는 UTC 기준이므로 KST 로 변환해야 날짜가 하루 밀리지 않는다.
        .collect(Collectors.groupingBy(item -> toKstDateTime(item.getDt()).toLocalDate()))
        .entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            entry ->
                CampWeatherResponseDto.WeatherDay.of(
                    entry.getKey(), pickRepresentative(entry.getValue())))
        .toList();
  }

  /** 정오에 가장 가까운 항목을 그날의 대표로 고른다. */
  private ForecastResponse.Item pickRepresentative(List<ForecastResponse.Item> items) {
    return items.stream()
        .min(
            Comparator.comparingInt(
                item -> Math.abs(toKstDateTime(item.getDt()).getHour() - REPRESENTATIVE_HOUR)))
        .orElse(items.get(0));
  }

  /** Unix 타임스탬프(초) → 한국 시각. 초 단위이므로 ofEpochSecond 를 쓴다(ofEpochMilli 를 쓰면 1970년이 나온다). */
  private LocalDateTime toKstDateTime(Long dt) {
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(dt), KST);
  }
}
