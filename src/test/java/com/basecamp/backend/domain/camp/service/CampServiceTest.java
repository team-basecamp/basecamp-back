package com.basecamp.backend.domain.camp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.storage.FileStorageService;
import com.basecamp.backend.domain.camp.client.kakao.GeoPoint;
import com.basecamp.backend.domain.camp.client.kakao.KakaoGeocodingClient;
import com.basecamp.backend.domain.camp.dto.request.CampRegistrationRequest;
import com.basecamp.backend.domain.camp.dto.request.CampUpdateRequest;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

/** 캠핑장 등록/수정 시 주소 기반 지오코딩 반영 단위 테스트. */
@ExtendWith(MockitoExtension.class)
class CampServiceTest {

  private static final Long OWNER_ID = 1L;

  @Mock private CampRepository campRepository;

  @Mock private RestTemplate restTemplate;

  @Mock private KakaoGeocodingClient kakaoGeocodingClient;

  @Mock private FileStorageService fileStorageService;

  @Mock private CampTransactionService campTransactionService;

  @InjectMocks private CampService campService;

  private CampRegistrationRequest.CampRegistrationRequestBuilder baseRequest() {
    return CampRegistrationRequest.builder()
        .facltNm("베이스캠프 오토캠핑장")
        .addr1("서울특별시 중구 세종대로 110")
        .tel("02-1234-5678")
        .induty("오토캠핑장")
        .price(50000);
  }

  @Test
  @DisplayName("registerCamp_지오코딩성공_좌표를저장한다")
  void registerCamp_지오코딩성공_좌표를저장한다() {
    // given
    GeoPoint geoPoint = new GeoPoint(new BigDecimal("126.9779692"), new BigDecimal("37.566535"));
    given(kakaoGeocodingClient.geocode("서울특별시 중구 세종대로 110")).willReturn(geoPoint);
    givenRegisterEchoesCamp();

    // when
    Camp saved = campService.registerCamp(baseRequest().build(), OWNER_ID, null);

    // then
    assertThat(saved.getMapX()).isEqualByComparingTo("126.9779692");
    assertThat(saved.getMapY()).isEqualByComparingTo("37.566535");
  }

  @Test
  @DisplayName("registerCamp_지오코딩실패_좌표없이등록은성공한다")
  void registerCamp_지오코딩실패_좌표없이등록은성공한다() {
    // given: 신축 건물 등 카카오가 못 찾는 주소는 null을 반환한다
    given(kakaoGeocodingClient.geocode(any())).willReturn(null);
    givenRegisterEchoesCamp();

    // when
    Camp saved = campService.registerCamp(baseRequest().build(), OWNER_ID, null);

    // then: 좌표만 비어있을 뿐 등록 자체는 막지 않는다
    assertThat(saved.getMapX()).isNull();
    assertThat(saved.getMapY()).isNull();
    assertThat(saved.getFacltNm()).isEqualTo("베이스캠프 오토캠핑장");
  }

  @Test
  @DisplayName("registerCamp_ownerId없음_ACCESS_DENIED를던지고지오코딩을호출하지않는다")
  void registerCamp_ownerId없음_ACCESS_DENIED를던진다() {
    // when & then
    assertThatThrownBy(() -> campService.registerCamp(baseRequest().build(), null, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(kakaoGeocodingClient, never()).geocode(any());
    verify(campTransactionService, never()).register(any(), any());
  }

  // 등록은 CampService 가 조립한 Camp 를 트랜잭션 협력 빈에 넘겨 저장한다.
  // 여기서는 그 협력 빈이 받은 엔티티를 그대로 돌려주게 해, 조립 결과(좌표 등)를 검증할 수 있게 한다.
  private void givenRegisterEchoesCamp() {
    given(campTransactionService.register(any(Camp.class), any()))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  // updateCamp 에서 CampService 가 지는 책임은 "외부 호출을 트랜잭션 밖에서 끝내고 그 결과를 넘기는 것"이다.
  // 좌표를 실제로 엔티티에 반영하는 일은 CampTransactionService 가 하므로, 그쪽은 CampTransactionServiceTest 에서 본다.

  @Test
  @DisplayName("updateCamp_주소변경_지오코딩결과를트랜잭션서비스에넘긴다")
  void updateCamp_주소변경_지오코딩결과를넘긴다() {
    // given
    GeoPoint newGeoPoint =
        new GeoPoint(new BigDecimal("129.0756416"), new BigDecimal("35.1795543"));
    given(kakaoGeocodingClient.geocode("부산광역시 해운대구 해운대해변로 264")).willReturn(newGeoPoint);
    givenUpdateReturnsEmptyResult();

    CampUpdateRequest request = CampUpdateRequest.builder().addr1("부산광역시 해운대구 해운대해변로 264").build();

    // when
    campService.updateCamp(100L, request, OWNER_ID, null);

    // then: 조회한 좌표가 그대로 전달된다
    verify(campTransactionService)
        .update(eq(100L), eq(request), eq(newGeoPoint), any(), eq(OWNER_ID));
  }

  @Test
  @DisplayName("updateCamp_주소변경_지오코딩실패_null좌표를넘긴다")
  void updateCamp_주소변경_지오코딩실패_null을넘긴다() {
    // given: 신축 건물 등 카카오가 못 찾는 주소는 null을 반환한다
    given(kakaoGeocodingClient.geocode("존재하지않는주소")).willReturn(null);
    givenUpdateReturnsEmptyResult();

    CampUpdateRequest request = CampUpdateRequest.builder().addr1("존재하지않는주소").build();

    // when
    campService.updateCamp(100L, request, OWNER_ID, null);

    // then: null 이 그대로 전달된다. 새 주소와 옛 좌표가 어긋나지 않도록 좌표를 비우는 것은 넘겨받은 쪽의 몫이다.
    verify(campTransactionService).update(eq(100L), eq(request), isNull(), any(), eq(OWNER_ID));
  }

  @Test
  @DisplayName("updateCamp_주소변경없음_지오코딩을호출하지않는다")
  void updateCamp_주소변경없음_지오코딩을호출하지않는다() {
    // given
    givenUpdateReturnsEmptyResult();
    CampUpdateRequest request = CampUpdateRequest.builder().price(20000).build();

    // when
    campService.updateCamp(100L, request, OWNER_ID, null);

    // then: 주소를 안 바꿨으면 외부 호출을 아낀다
    verify(kakaoGeocodingClient, never()).geocode(any());
    verify(campTransactionService).update(eq(100L), eq(request), isNull(), any(), eq(OWNER_ID));
  }

  private void givenUpdateReturnsEmptyResult() {
    given(campTransactionService.update(any(), any(), any(), any(), any()))
        .willReturn(new CampTransactionService.UpdateResult(Camp.builder().build(), List.of()));
  }
}
