package com.basecamp.backend.domain.camp.client.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 주소 검색 API로 주소 문자열을 좌표로 변환한다(지오코딩).
 *
 * <p>주소를 찾지 못하거나 API 호출이 실패해도 예외를 던지지 않고 {@code null}을 반환한다. 좌표는 캠핑장 등록/수정을 막을 만큼 필수적인 정보가 아니며, 지도
 * 화면은 이미 좌표가 없는 캠핑장을 자연스럽게 걸러내도록 되어 있기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoGeocodingClient {

  // 카카오 주소 검색(로컬) API 엔드포인트. query 파라미터로 넘긴 주소 문자열을 검색한다.
  private static final String ADDRESS_SEARCH_URL =
      "https://dapi.kakao.com/v2/local/search/address.json";

  // 실제 HTTP 호출을 수행하는 스프링 빈. RestTemplateConfig 에서 등록해둔 것을 그대로 주입받는다.
  private final RestTemplate restTemplate;

  // application.yml 의 kakao.local.api-key 값을 주입받는다.
  // 카카오 로그인의 client-id와 같은 값(앱당 하나 발급되는 REST API 키)이며,
  // 콘솔에서 "카카오맵" 제품을 켜야 이 키로 주소 검색 API 호출이 허용된다.
  @Value("${kakao.local.api-key}")
  private String apiKey;

  // 주소 문자열 하나를 좌표(GeoPoint)로 바꿔서 돌려준다.
  // 실패(주소를 못 찾음/네트워크 오류/인증 실패 등) 시에는 예외를 던지지 않고 null을 반환해서,
  // 호출한 쪽(CampService)이 좌표 없이도 등록/수정을 계속 진행할 수 있게 한다.
  public GeoPoint geocode(String address) {
    // 주소 자체가 비어있으면 API를 호출할 이유가 없다.
    if (address == null || address.isBlank()) {
      return null;
    }

    try {
      // 1) 요청 URL 조립: ...address.json?query=<주소>
      //    UriComponentsBuilder가 한글/공백 등을 알아서 URL 인코딩해준다.
      String url =
          UriComponentsBuilder.fromHttpUrl(ADDRESS_SEARCH_URL)
              .queryParam("query", address)
              .build()
              .toUriString();

      // 2) 인증 헤더 설정: 카카오 API는 Bearer가 아니라 "KakaoAK {키}" 형식을 요구한다.
      HttpHeaders headers = new HttpHeaders();
      headers.set(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey);

      // 3) GET 요청 실행. getForEntity 대신 exchange를 쓰는 이유는
      //    커스텀 헤더(Authorization)를 함께 보내야 하기 때문이다.
      ResponseEntity<KakaoAddressSearchResponse> response =
          restTemplate.exchange(
              url, HttpMethod.GET, new HttpEntity<>(headers), KakaoAddressSearchResponse.class);

      // 4) 검색 결과가 하나도 없으면(주소를 못 찾음) 좌표 없이 null 반환
      List<Document> documents = response.getBody() != null ? response.getBody().documents() : null;
      if (documents == null || documents.isEmpty()) {
        log.warn("카카오 지오코딩 결과 없음 - 주소: {}", address);
        return null;
      }

      // 5) 가장 정확도 높은 첫 번째 결과를 사용. x=경도(mapX), y=위도(mapY)로 매핑한다.
      //    좌표 필드가 비어있는 응답도 있을 수 있어 파싱 전에 확인한다.
      Document first = documents.get(0);
      if (first.x() == null || first.x().isBlank() || first.y() == null || first.y().isBlank()) {
        log.warn("카카오 지오코딩 좌표 필드 누락 - 주소: {}", address);
        return null;
      }
      return new GeoPoint(new BigDecimal(first.x()), new BigDecimal(first.y()));

    } catch (RestClientException | NumberFormatException e) {
      // 네트워크 오류, 401/403(키 미인증), 응답 파싱 실패 등 — 전부 등록을 막지 않고 넘어간다.
      log.warn("카카오 지오코딩 실패 - 주소: {}, 원인: {}", address, e.getClass().getSimpleName());
      return null;
    }
  }

  // 카카오 주소 검색 API 응답 구조: { "documents": [ { "x": "경도", "y": "위도", ... } ], "meta": {...} }
  // 여기 선언되지 않은 필드는 무시한다
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record KakaoAddressSearchResponse(List<Document> documents) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Document(@JsonProperty("x") String x, @JsonProperty("y") String y) {}
}
