package com.basecamp.backend.domain.weather.controller;

import com.basecamp.backend.domain.weather.dto.response.RegionWeatherResponseDto;
import com.basecamp.backend.domain.weather.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Weather", description = "날씨 조회")
@RestController
@RequestMapping("/api/v1/weather")
@RequiredArgsConstructor
public class WeatherController {

  private final WeatherService weatherService;

  @Operation(
      summary = "시/도별 현재 날씨 조회",
      description = "홈페이지 위젯용. 전국 시/도의 현재 날씨를 조회합니다. 비로그인도 이용할 수 있습니다.")
  @GetMapping("/regions")
  public ResponseEntity<List<RegionWeatherResponseDto>> getRegionWeathers() {
    return ResponseEntity.ok(weatherService.getRegionWeathers());
  }
}
