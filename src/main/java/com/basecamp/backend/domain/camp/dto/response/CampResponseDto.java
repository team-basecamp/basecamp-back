package com.basecamp.backend.domain.camp.dto.response;

import com.basecamp.backend.domain.camp.entity.Camp;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    public static CampResponseDto from(Camp camp) {
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
                .manageSttus(camp.getManageSttus())
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
                .operDeCl(camp.getOperDeCl())
                .build();
    }

    // "전기,샤워장,화장실" -> ["전기", "샤워장", "화장실"], null/빈 문자열이면 빈 리스트
    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
