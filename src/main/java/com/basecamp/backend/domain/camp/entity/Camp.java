package com.basecamp.backend.domain.camp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "camps")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Camp {

    //PK 캠핑장 데이터베이스 고유번호
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "camp_id")
    private Long campId;

    // 고캠핑 API의 고유ID
    // 중복 확인을 할 때 사용
    @Column(name = "content_id",unique = true)
    private Long contentId;

    // 캠핑장 이름
    @Column(name = "faclt_nm" , nullable = false,length = 100)
    private String facltNm;

    // 도로명 / 지번 주소 ( 기본 주소 )
    @Column(name = "addr1" , nullable = false , length = 200)
    private String addr1;

    // 상세주소
    @Column(name = "addr2" , length = 200)
    private String addr2;

    //GPS 경도
    @Column(name = "map_x")
    private BigDecimal mapX;

    //GPS 위도
    @Column(name = "map_y")
    private BigDecimal mapY;

    // 캠핑장 연락처
    @Column(name = "tel", length = 20)
    private String tel;

    // 캠핑장 종류
    @Column(name = "induty", length = 100)
    private String induty;

    // 일반 야영장 사이트 갯수
    @Column(name = "gnrl_site_co")
    private Integer gnrlSiteCo;

    // 오토캠핑 사이트 갯수
    @Column(name = "auto_site_co")
    private Integer autoSiteCo;

    // 글램핑 사이트 갯수
    @Column(name = "glamp_site_co")
    private Integer glampSiteCo;

    // 캠핑장 대표 이미지 URL
    @Column(name = "first_image_url", columnDefinition = "TEXT")
    private String firstImageUrl;

    // 캠핑장 운영 상태
    @Column(name = "manage_sttus", length = 20, nullable = false)
    private String manageSttus;

    // 1박 가격
    @Column(name = "price", nullable = false)
    private Integer price;

    // 리뷰 평균 평점
    @Column(name = "average_rating", columnDefinition = "DECIMAL(3,2)")
    private BigDecimal averageRating;

    // 누적 예약 건수
    @Column(name = "reservation_count", nullable = false)
    private Integer reservationCount;

    // 데이터 생성 시간
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 데이터 수정 시간
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // DTO 에서 엔티티로 변환
    // 고캠핑 API 데이터를 받아서 camp 엔티티로 변환
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
                .price(0)  // 기본값
                .averageRating(new BigDecimal("0.0"))  // 기본값
                .reservationCount(0)  // 기본값
                .createdAt(LocalDateTime.now())
                .build();
    }
}
