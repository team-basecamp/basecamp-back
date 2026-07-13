package com.basecamp.backend.domain.camp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 생성 경로를 builder()/정적 팩토리(fromGocampingApi 등)로만 제한한다.
// no-args 생성자는 JPA가 리플렉션으로 엔티티를 로딩할 때만 필요해 protected로 좁혔다.
// all-args 생성자는 @Builder가 내부적으로 써야 해서 없앨 수는 없지만, private으로 좁혀서
// 외부에서 필드를 순서대로 나열해 직접 생성하는 경로(순서 실수 위험)는 막았다.
@Entity
@Table(name = "camps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    // price: 고캠핑 API가 가격 정보를 제공하지 않아, 서비스 계층에서 정책에 따라 결정한 값을 받아 조립만 한다.
    public static Camp fromGocampingApi(com.basecamp.backend.domain.camp.dto.request.GocampingApiResponseDto dto, int price) {
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
                .price(price)
                .averageRating(new BigDecimal("0.0"))
                .reservationCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // 가격이 아직 채워지지 않은(0) 캠핑장에 한해서만 값을 채운다. 실제 가격이 있는 캠핑장은 보호한다.
    public void assignDefaultPriceIfMissing(int price) {
        if (this.price == 0) {
            this.price = price;
        }
    }

    // 고캠핑 API 원본 데이터가 컬럼 길이 제한을 넘는 경우가 있어 저장 전 자른다 (예: intro가 500자 초과).
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}