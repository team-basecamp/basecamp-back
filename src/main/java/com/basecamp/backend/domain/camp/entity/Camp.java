package com.basecamp.backend.domain.camp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "camps")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Camp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "camp_id")
    private Long campId;

    @Column(name = "content_id", unique = true)
    private Long contentId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "faclt_nm", length = 100)
    private String facltNm;

    @Column(name = "addr1", length = 200)
    private String addr1;

    @Column(name = "addr2", length = 200)
    private String addr2;

    @Column(name = "map_x")
    private BigDecimal mapX;

    @Column(name = "map_y")
    private BigDecimal mapY;

    @Column(name = "tel", length = 20)
    private String tel;

    @Column(name = "induty", length = 100)
    private String induty;

    @Column(name = "gnrl_site_co")
    private Integer gnrlSiteCo;

    @Column(name = "auto_site_co")
    private Integer autoSiteCo;

    @Column(name = "glamp_site_co")
    private Integer glampSiteCo;

    @Column(name = "first_image_url", columnDefinition = "TEXT")
    private String firstImageUrl;

    @Column(name = "manage_sttus", length = 20, nullable = false)
    private String manageSttus;

    @Column(name = "lineIntro", length = 500)
    private String lineIntro;

    @Column(name = "homepage", length = 255)
    private String homepage;

    @Column(name = "doNm", length = 50)
    private String doNm;

    @Column(name = "sbrsCl", length = 500)
    private String sbrsCl;

    @Column(name = "toiletCo")
    private Integer toiletCo;

    @Column(name = "swrmCo")
    private Integer swrmCo;

    @Column(name = "wtrplCo")
    private Integer wtrplCo;

    @Column(name = "extshrCo")
    private Integer extshrCo;

    @Column(name = "glampInnerFclty", length = 500)
    private String glampInnerFclty;

    @Column(name = "caravInnerFclty", length = 500)
    private String caravInnerFclty;

    @Column(name = "operDeCl", length = 50)
    private String operDeCl;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "average_rating", columnDefinition = "DECIMAL(3,2)")
    private BigDecimal averageRating;

    @Column(name = "reservation_count", nullable = false)
    private Integer reservationCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Camp fromGocampingApi(com.basecamp.backend.domain.camp.dto.request.GocampingApiResponseDto dto) {
        return Camp.builder()
                .contentId(dto.getContentId())
                .facltNm(dto.getFacltNm())
                .addr1(dto.getAddr1())
                .mapX(dto.getMapX() != null ? new BigDecimal(dto.getMapX().toString()) : null)
                .mapY(dto.getMapY() != null ? new BigDecimal(dto.getMapY().toString()) : null)
                .tel(dto.getTel())
                .induty(dto.getInduty())
                .gnrlSiteCo(dto.getGnrlSiteCo())
                .autoSiteCo(dto.getAutoSiteCo())
                .glampSiteCo(dto.getGlampSiteCo())
                .firstImageUrl(dto.getFirstImageUrl())
                .manageSttus(dto.getManageSttus())
                .lineIntro(truncate(dto.getIntro(), 500))
                .homepage(truncate(dto.getHomepage(), 255))
                .sbrsCl(truncate(dto.getSbrsCl(), 500))
                .price(0)
                .averageRating(new BigDecimal("0.0"))
                .reservationCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // 고캠핑 API 원본 데이터가 컬럼 길이 제한을 넘는 경우가 있어 저장 전 자른다 (예: intro가 500자 초과).
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}