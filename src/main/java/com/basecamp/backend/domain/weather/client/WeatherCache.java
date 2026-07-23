package com.basecamp.backend.domain.weather.client;

import com.basecamp.backend.domain.weather.dto.response.CampWeatherResponseDto;
import com.basecamp.backend.domain.weather.dto.response.RegionWeatherResponseDto;
import com.basecamp.backend.domain.weather.entity.Region;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 시/도별 날씨 조회 캐시.
 *
 * <p>날씨는 외부(OpenWeatherMap)가 가진 데이터를 잠시 빌려온 것이고 몇 분만 지나도 낡는다. 우리 서비스의 진실 데이터가 아니므로 DB(MySQL)가 아니라
 * TTL 이 붙는 캐시에 둔다. 오래된 항목은 TTL 로 자동 정리되므로 별도의 배치 삭제가 필요 없다.
 *
 * <p><b>키 하나에 지역 하나의 날씨를 통째로(JSON) 담는다.</b> 필드별로 쪼개면 조회가 4배로 늘고, 일부 필드만 만료돼 "기온은 없는데 아이콘은 맑음" 같은 반쪽
 * 데이터가 생긴다.
 *
 * <p><b>장애 정책:</b> 읽기·쓰기 모두 실패해도 예외를 던지지 않는다(fail-soft). 캐시를 못 써도 외부 API 를 직접 부르면 되므로, Redis 장애가 날씨
 * 기능을 멈출 이유가 없다. ({@code TokenBlacklistCache} 가 쓰기 실패를 전파하는 것과 대비된다 — 폐기됐어야 할 토큰이 살아나면 안 되기 때문이다.
 * 같은 캐시라도 담는 데이터의 성격에 따라 정책이 다르다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherCache {

  private static final String CAMP_KEY_PREFIX = "weather:camp:";
  private static final String KEY_PREFIX = "weather:region:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  /** 캐시 유지 시간(분). 시/도 대표 날씨는 자주 안 바뀌므로 넉넉히 잡는다. */
  @Value("${openweather.cache.region-ttl-minutes:30}")
  private long ttlMinutes;

  /** 캐시된 날씨를 꺼낸다. 없거나 실패하면 null(호출자가 API 를 직접 부른다). */
  public RegionWeatherResponseDto get(Region region) {
    try {
      String json = redisTemplate.opsForValue().get(key(region));
      if (json == null) {
        return null;
      }
      return objectMapper.readValue(json, RegionWeatherResponseDto.class);
    } catch (JsonProcessingException | RuntimeException e) {
      log.warn("날씨 캐시 조회 실패. region={}, 원인={}", region, e.getClass().getSimpleName());
      return null;
    }
  }

  /** 날씨를 캐시에 저장한다. 실패해도 조용히 넘어간다(다음 요청이 API 를 부르면 그만이다). */
  public void put(Region region, RegionWeatherResponseDto dto) {
    try {
      String json = objectMapper.writeValueAsString(dto);
      redisTemplate.opsForValue().set(key(region), json, Duration.ofMinutes(ttlMinutes));
    } catch (JsonProcessingException | RuntimeException e) {
      log.warn("날씨 캐시 저장 실패. region={}, 원인={}", region, e.getClass().getSimpleName());
    }
  }

  private String key(Region region) {
    return KEY_PREFIX + region.name();
  }

  /** 캠핑장 예보 캐시 유지 시간(분). 지역 위젯보다 짧게 잡는다(캠핑장 수 × 조회가 많아 신선도가 더 중요). */
  @Value("${openweather.cache.camp-ttl-minutes:10}")
  private long campTtlMinutes;

  /** 캐시된 캠핑장 5일 예보를 꺼낸다. 없거나 실패하면 null. */
  public List<CampWeatherResponseDto.WeatherDay> getCampForecast(Long campId) {
    try {
      String json = redisTemplate.opsForValue().get(campKey(campId));
      if (json == null) {
        return null;
      }
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException | RuntimeException e) {
      log.warn("캠핑장 날씨 캐시 조회 실패. campId={}, 원인={}", campId, e.getClass().getSimpleName());
      return null;
    }
  }

  /** 캠핑장 5일 예보를 캐시에 저장한다. 실패해도 조용히 넘어간다. */
  public void putCampForecast(Long campId, List<CampWeatherResponseDto.WeatherDay> forecast) {
    try {
      String json = objectMapper.writeValueAsString(forecast);
      redisTemplate.opsForValue().set(campKey(campId), json, Duration.ofMinutes(campTtlMinutes));
    } catch (JsonProcessingException | RuntimeException e) {
      log.warn("캠핑장 날씨 캐시 저장 실패. campId={}, 원인={}", campId, e.getClass().getSimpleName());
    }
  }

  private String campKey(Long campId) {
    return CAMP_KEY_PREFIX + campId;
  }
}
