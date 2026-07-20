package com.basecamp.backend.domain.camp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.client.kakao.GeoPoint;
import com.basecamp.backend.domain.camp.client.kakao.KakaoGeocodingClient;
import com.basecamp.backend.domain.camp.dto.request.CampRegistrationRequest;
import com.basecamp.backend.domain.camp.dto.request.CampUpdateRequest;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import java.math.BigDecimal;
import java.util.Optional;
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
    given(campRepository.save(any(Camp.class))).willAnswer(invocation -> invocation.getArgument(0));

    // when
    Camp saved = campService.registerCamp(baseRequest().build(), OWNER_ID);

    // then
    assertThat(saved.getMapX()).isEqualByComparingTo("126.9779692");
    assertThat(saved.getMapY()).isEqualByComparingTo("37.566535");
  }

  @Test
  @DisplayName("registerCamp_지오코딩실패_좌표없이등록은성공한다")
  void registerCamp_지오코딩실패_좌표없이등록은성공한다() {
    // given: 신축 건물 등 카카오가 못 찾는 주소는 null을 반환한다
    given(kakaoGeocodingClient.geocode(any())).willReturn(null);
    given(campRepository.save(any(Camp.class))).willAnswer(invocation -> invocation.getArgument(0));

    // when
    Camp saved = campService.registerCamp(baseRequest().build(), OWNER_ID);

    // then: 좌표만 비어있을 뿐 등록 자체는 막지 않는다
    assertThat(saved.getMapX()).isNull();
    assertThat(saved.getMapY()).isNull();
    assertThat(saved.getFacltNm()).isEqualTo("베이스캠프 오토캠핑장");
  }

  @Test
  @DisplayName("registerCamp_ownerId없음_ACCESS_DENIED를던지고지오코딩을호출하지않는다")
  void registerCamp_ownerId없음_ACCESS_DENIED를던진다() {
    // when & then
    assertThatThrownBy(() -> campService.registerCamp(baseRequest().build(), null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(kakaoGeocodingClient, never()).geocode(any());
    verify(campRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateCamp_주소변경_좌표를다시조회해반영한다")
  void updateCamp_주소변경_좌표를다시조회해반영한다() {
    // given
    Camp existing =
        Camp.builder()
            .facltNm("기존이름")
            .addr1("기존주소")
            .ownerId(OWNER_ID)
            .mapX(new BigDecimal("1"))
            .mapY(new BigDecimal("1"))
            .price(10000)
            .build();
    given(campRepository.findById(100L)).willReturn(Optional.of(existing));

    GeoPoint newGeoPoint =
        new GeoPoint(new BigDecimal("129.0756416"), new BigDecimal("35.1795543"));
    given(kakaoGeocodingClient.geocode("부산광역시 해운대구 해운대해변로 264")).willReturn(newGeoPoint);
    given(campRepository.save(any(Camp.class))).willAnswer(invocation -> invocation.getArgument(0));

    CampUpdateRequest request = CampUpdateRequest.builder().addr1("부산광역시 해운대구 해운대해변로 264").build();

    // when
    Camp updated = campService.updateCamp(100L, request, OWNER_ID);

    // then
    assertThat(updated.getAddr1()).isEqualTo("부산광역시 해운대구 해운대해변로 264");
    assertThat(updated.getMapX()).isEqualByComparingTo("129.0756416");
    assertThat(updated.getMapY()).isEqualByComparingTo("35.1795543");
  }

  @Test
  @DisplayName("updateCamp_주소변경_지오코딩실패_옛좌표를지운다")
  void updateCamp_주소변경_지오코딩실패_옛좌표를지운다() {
    // given: 이전 주소 기준 좌표가 이미 있는 상태에서 주소를 변경하는데, 새 주소는 지오코딩에 실패한다
    Camp existing =
        Camp.builder()
            .facltNm("기존이름")
            .addr1("기존주소")
            .ownerId(OWNER_ID)
            .mapX(new BigDecimal("1"))
            .mapY(new BigDecimal("1"))
            .price(10000)
            .build();
    given(campRepository.findById(100L)).willReturn(Optional.of(existing));
    given(kakaoGeocodingClient.geocode("존재하지않는주소")).willReturn(null);
    given(campRepository.save(any(Camp.class))).willAnswer(invocation -> invocation.getArgument(0));

    CampUpdateRequest request = CampUpdateRequest.builder().addr1("존재하지않는주소").build();

    // when
    Camp updated = campService.updateCamp(100L, request, OWNER_ID);

    // then: 새 주소와 옛 좌표가 어긋난 채로 남지 않도록 좌표를 비운다
    assertThat(updated.getAddr1()).isEqualTo("존재하지않는주소");
    assertThat(updated.getMapX()).isNull();
    assertThat(updated.getMapY()).isNull();
  }

  @Test
  @DisplayName("updateCamp_주소변경없음_지오코딩을호출하지않는다")
  void updateCamp_주소변경없음_지오코딩을호출하지않는다() {
    // given
    Camp existing =
        Camp.builder().facltNm("기존이름").addr1("기존주소").ownerId(OWNER_ID).price(10000).build();
    given(campRepository.findById(100L)).willReturn(Optional.of(existing));
    given(campRepository.save(any(Camp.class))).willAnswer(invocation -> invocation.getArgument(0));

    CampUpdateRequest request = CampUpdateRequest.builder().price(20000).build();

    // when
    campService.updateCamp(100L, request, OWNER_ID);

    // then
    verify(kakaoGeocodingClient, never()).geocode(any());
  }
}
