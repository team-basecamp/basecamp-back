package com.basecamp.backend.domain.payment.client;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.payment.client.dto.PortOnePayment;
import com.basecamp.backend.domain.payment.config.PortOneProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

/**
 * 포트원(PortOne) V2 REST API 클라이언트.
 *
 * <p>테스트 모드라도 호출 경로·인증·응답 구조는 실거래와 동일하다. 콘솔에서 테스트 채널을 쓰면 실제 승인만 일어나지 않을 뿐, 우리 서버 코드는 그대로 운영에 넘어간다.
 *
 * <p><b>클라이언트가 예외를 던지는 이유:</b> 지오코딩처럼 실패해도 넘어갈 수 있는 부가 기능과 달리, 결제 검증은 실패하면 반드시 멈춰야 한다. 포트원 응답을 못
 * 받았는데 "결제됐다"고 처리하면 돈을 안 받고 예약을 확정하게 된다. 그래서 모든 실패는 예외로 올린다.
 */
@Slf4j
@Component
public class PortOneClient {

  private final RestClient restClient;
  private final PortOneProperties properties;
  private final ObjectMapper objectMapper;

  public PortOneClient(
      RestClient restClient, PortOneProperties properties, ObjectMapper objectMapper) {
    this.restClient = restClient;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  /**
   * 결제 건 단건 조회. 프론트가 알려준 결제 결과를 <b>믿지 않고</b> 서버가 직접 확인하는 용도다.
   *
   * <p>프론트 응답은 사용자가 위조할 수 있으므로, 결제 완료 여부와 금액은 반드시 이 조회 결과로 판단한다.
   */
  public PortOnePayment getPayment(String pgPaymentId) {
    String url = properties.apiBaseUrl() + "/payments/" + encodePathSegment(pgPaymentId);

    try {
      return restClient
          .get()
          .uri(url)
          .header(HttpHeaders.AUTHORIZATION, authorization())
          .retrieve()
          .onStatus(
              HttpStatus.NOT_FOUND::equals,
              (req, res) -> {
                throw new BusinessException(ErrorCode.PG_PAYMENT_NOT_FOUND);
              })
          .body(PortOnePayment.class);
    } catch (BusinessException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("포트원 결제 조회 실패 - pgPaymentId: {}, 원인: {}", pgPaymentId, e.getMessage());
      throw new BusinessException(ErrorCode.PG_COMMUNICATION_FAILED);
    }
  }

  /**
   * 결제 취소(환불) 요청. 전액 취소만 사용한다(부분 환불 정책이 아직 없다).
   *
   * <p>이미 취소된 건에 다시 취소를 걸면 포트원이 {@code PaymentAlreadyCancelledError} 를 준다. 이는 우리가 원하는 최종 상태와 같으므로
   * 성공으로 간주한다 — 스케줄러 재실행이나 웹훅 중복 수신으로 취소가 두 번 호출돼도 환불 처리가 막히지 않아야 한다.
   */
  public void cancelPayment(String pgPaymentId, String reason) {
    String url =
        properties.apiBaseUrl() + "/payments/" + encodePathSegment(pgPaymentId) + "/cancel";

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("storeId", properties.storeId());
    body.put("reason", reason);
    body.put("requester", "ADMIN"); // 고객 요청이든 자동 반려든, 취소 API를 호출하는 주체는 우리 서버다.

    try {
      restClient
          .post()
          .uri(url)
          .header(HttpHeaders.AUTHORIZATION, authorization())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .onStatus(
              status -> status.isSameCodeAs(HttpStatus.CONFLICT),
              (req, res) -> {
                String payload = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                if (alreadyCancelled(payload)) {
                  log.info("포트원 결제가 이미 취소된 상태 - pgPaymentId: {} (환불 처리 계속)", pgPaymentId);
                  return; // 예외를 던지지 않으면 정상 응답으로 이어진다.
                }
                log.error("포트원 결제 취소 거부 - pgPaymentId: {}, 응답: {}", pgPaymentId, payload);
                throw new BusinessException(ErrorCode.PG_REFUND_FAILED);
              })
          .toBodilessEntity();
    } catch (BusinessException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("포트원 결제 취소 실패 - pgPaymentId: {}, 원인: {}", pgPaymentId, e.getMessage());
      throw new BusinessException(ErrorCode.PG_REFUND_FAILED);
    }
  }

  // 포트원 인증 헤더는 Bearer 가 아니라 "PortOne {API Secret}" 형식이다.
  private String authorization() {
    String secret = properties.apiSecret();
    if (secret == null || secret.isBlank()) {
      throw new BusinessException(ErrorCode.PG_NOT_CONFIGURED);
    }
    return "PortOne " + secret;
  }

  // 결제 건 ID는 우리가 만들지만(영숫자+언더스코어), 경로에 그대로 끼우지 않고 인코딩해 둔다.
  private String encodePathSegment(String value) {
    return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
  }

  // 취소 실패 응답이 "이미 취소됨"인지 판별. 응답 본문의 type 필드가 오류 종류를 담는다.
  private boolean alreadyCancelled(String payload) {
    try {
      JsonNode type = objectMapper.readTree(payload).path("type");
      return "PAYMENT_ALREADY_CANCELLED".equals(type.asText())
          || "PaymentAlreadyCancelledError".equals(type.asText());
    } catch (Exception e) {
      return false;
    }
  }
}
