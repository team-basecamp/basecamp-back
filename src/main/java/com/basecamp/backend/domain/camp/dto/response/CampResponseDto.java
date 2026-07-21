package com.basecamp.backend.domain.camp.dto.response;

import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.user.entity.Image;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// 캠핑장 조회 응답 DTO. 목록/HOT/상세 조회에서 공통으로 사용한다.
@Getter
@Builder
public class CampResponseDto {

  private Long campId;
  private Long contentId;
  private String facltNm;
  private String addr1;
  private String addr2;
  private BigDecimal mapX;
  private BigDecimal mapY;
  private String tel;
  private String induty;
  private Integer gnrlSiteCo;
  private Integer autoSiteCo;
  private Integer glampSiteCo;
  private String firstImageUrl;

  /**
   * 캠핑장 이미지 전체(갤러리). 상세 조회에서만 채워지고, 목록 조회에서는 {@code null} 이다.
   *
   * <p>목록은 캠핑장마다 이미지를 끌고 오면 N+1 이 되고 대표 한 장이면 충분하므로 {@link #firstImageUrl} 만 내려간다. 직접 등록하지 않은(고캠핑)
   * 캠핑장은 상세에서도 빈 목록이다 — 이미지가 제공자 서버에 있어 우리 저장소에 올린 것이 없기 때문이다.
   */
  private List<String> imageUrls;

  private String manageSttus;
  private Integer price;
  private BigDecimal averageRating;
  private Integer reservationCount;
  private LocalDateTime createdAt;
  private String lineIntro;
  private String homepage;
  private String doNm;
  private List<String> facilities;
  private Integer toiletCo;
  private Integer swrmCo;
  private Integer wtrplCo;
  private Integer extshrCo;
  private List<String> glampInnerFclty;
  private List<String> caravInnerFclty;
  private String operDeCl;

  /**
   * 목록·요약용 변환. 이미지 컬렉션을 건드리지 않는다.
   *
   * <p>캠핑장 목록은 서비스가 엔티티를 반환해 컨트롤러에서 변환하는데, {@code open-in-view: false} 라 그 시점에는 트랜잭션이 닫혀 있다. 여기서
   * {@code camp.getImages()} 를 읽으면 지연 로딩이 터진다. 갤러리가 필요한 상세 조회는 {@link #withImages(Camp)} 를 쓴다.
   */
  public static CampResponseDto from(Camp camp) {
    return baseBuilder(camp).build();
  }

  /**
   * 상세용 변환. 갤러리까지 채운다.
   *
   * <p>이미지가 초기화된 캠핑장에만 쓸 수 있다. {@code CampRepository.findWithImagesBy...} 로 조회한 것이어야 한다.
   */
  public static CampResponseDto withImages(Camp camp) {
    return baseBuilder(camp)
        .imageUrls(camp.getImages().stream().map(Image::getImageUrl).toList())
        .build();
  }

  private static CampResponseDtoBuilder baseBuilder(Camp camp) {
    return CampResponseDto.builder()
        .campId(camp.getCampId())
        .contentId(camp.getContentId())
        .facltNm(camp.getFacltNm())
        .addr1(camp.getAddr1())
        .addr2(camp.getAddr2())
        .mapX(camp.getMapX())
        .mapY(camp.getMapY())
        .tel(camp.getTel())
        .induty(camp.getInduty())
        .gnrlSiteCo(camp.getGnrlSiteCo())
        .autoSiteCo(camp.getAutoSiteCo())
        .glampSiteCo(camp.getGlampSiteCo())
        .firstImageUrl(camp.getFirstImageUrl())
        .manageSttus(camp.getManageSttus() != null ? camp.getManageSttus().getLabel() : null)
        .price(camp.getPrice())
        .averageRating(camp.getAverageRating())
        .reservationCount(camp.getReservationCount())
        .createdAt(camp.getCreatedAt())
        .lineIntro(camp.getLineIntro())
        .homepage(camp.getHomepage())
        .doNm(camp.getDoNm())
        .facilities(splitCsv(camp.getSbrsCl()))
        .toiletCo(camp.getToiletCo())
        .swrmCo(camp.getSwrmCo())
        .wtrplCo(camp.getWtrplCo())
        .extshrCo(camp.getExtshrCo())
        .glampInnerFclty(splitCsv(camp.getGlampInnerFclty()))
        .caravInnerFclty(splitCsv(camp.getCaravInnerFclty()))
        .operDeCl(camp.getOperDeCl());
  }

  // "전기,샤워장,화장실" -> ["전기", "샤워장", "화장실"], null/빈 문자열이면 빈 리스트
  private static List<String> splitCsv(String csv) {
    if (csv == null || csv.isBlank()) {
      return Collections.emptyList();
    }
    return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }
}
