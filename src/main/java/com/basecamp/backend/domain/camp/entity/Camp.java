package com.basecamp.backend.domain.camp.entity;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.client.kakao.GeoPoint;
import com.basecamp.backend.domain.camp.dto.request.CampUpdateRequest;
import com.basecamp.backend.domain.user.entity.Image;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.util.StringUtils;

// 생성 경로를 builder()/정적 팩토리(fromGocampingApi 등)로만 제한한다.
// no-args 생성자는 JPA가 리플렉션으로 엔티티를 로딩할 때만 필요해 protected로 좁혔다.
// all-args 생성자는 @Builder가 내부적으로 써야 해서 없앨 수는 없지만, private으로 좁혀서
// 외부에서 필드를 순서대로 나열해 직접 생성하는 경로(순서 실수 위험)는 막았다.
@Entity
@Table(name = "camps")
@SQLRestriction("deleted_at IS NULL")
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

  /**
   * 대표 이미지 URL. 출처를 가리지 않는다 — 고캠핑 API 로 들여온 캠핑장은 제공자의 외부 URL 이, 직접 등록한 캠핑장은 {@link #images} 첫 장의
   * 저장소 URL 이 들어온다.
   *
   * <p>{@link #images} 와 값이 겹치는 비정규화다. 목록 조회는 캠핑장을 엔티티로 반환해 컨트롤러에서 DTO 로 바꾸는데, {@code open-in-view:
   * false} 라 그 시점에는 이미 트랜잭션이 닫혀 있어 지연 로딩된 컬렉션을 만질 수 없다. 목록마다 이미지를 함께 로딩하면 N+1 도 따라온다. 대표 한 장을 여기
   * 복사해 두면 목록 경로는 컬렉션을 건드릴 일이 없다.
   *
   * <p>대신 쓰기 시점에 동기화 책임이 생긴다. {@link #attachImages}/{@link #replaceImages} 가 함께 갱신한다.
   */
  @Column(name = "first_image_url", columnDefinition = "TEXT")
  private String firstImageUrl;

  /**
   * 캠핑장 이미지 (1 캠핑장 : N 이미지). 직접 등록한 캠핑장에만 쓴다.
   *
   * <p>고캠핑 API 로 들여온 캠핑장은 이미지가 제공자 서버에 있어 우리 저장소에 올릴 것이 없다. 그런 캠핑장은 이 컬렉션이 비어 있고 {@link
   * #firstImageUrl} 만 채워진다.
   *
   * <p>게시글/리뷰와 같은 구조다 — 공용 저장소(images)와 중간 테이블(camp_images)을 {@code @OrderColumn} 으로 이어 첨부 순서를
   * 보존한다.
   */
  @Builder.Default
  @ManyToMany(cascade = CascadeType.PERSIST)
  @JoinTable(
      name = "camp_images",
      joinColumns = @JoinColumn(name = "camp_id"),
      inverseJoinColumns = @JoinColumn(name = "image_id"))
  @OrderColumn(name = "sort_order")
  private List<Image> images = new ArrayList<>();

  @Column(name = "manage_sttus", length = 20, nullable = false)
  private CampManageStatus manageSttus;

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

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public void softDelete() {
    if (this.deletedAt != null) {
      throw new BusinessException(ErrorCode.ALREADY_DELETED_CAMP);
    }
    this.deletedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
  }

  // 캠핑장 정보 수정
  public void updateInfo(CampUpdateRequest request) {
    // 이름
    if (request.getFacltNm() != null) {
      this.facltNm = request.getFacltNm();
    }
    // 주소
    if (request.getAddr1() != null) {
      this.addr1 = request.getAddr1();
    }
    if (request.getAddr2() != null) {
      this.addr2 = request.getAddr2();
    }
    // 전화번호
    if (request.getTel() != null) {
      this.tel = request.getTel();
    }
    // 캠핑장 유형
    if (request.getInduty() != null) {
      this.induty = request.getInduty();
    }
    // 가격
    if (request.getPrice() != null) {
      this.price = request.getPrice();
    }
    // 사이트 개수들
    if (request.getGnrlSiteCo() != null) {
      this.gnrlSiteCo = request.getGnrlSiteCo();
    }
    if (request.getAutoSiteCo() != null) {
      this.autoSiteCo = request.getAutoSiteCo();
    }
    if (request.getGlampSiteCo() != null) {
      this.glampSiteCo = request.getGlampSiteCo();
    }
    // 한줄 소개
    if (request.getLineIntro() != null) {
      this.lineIntro = request.getLineIntro();
    }
    // 대표 이미지 URL
    if (request.getFirstImageUrl() != null) {
      this.firstImageUrl = request.getFirstImageUrl();
    }
    // 웹사이트
    if (request.getHomepage() != null) {
      this.homepage = request.getHomepage();
    }
    // 수정 시각 갱신
    this.updatedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
  }

  /**
   * 이미지를 첨부한다. 전달 순서가 곧 노출 순서다. 비어 있으면 아무것도 하지 않는다.
   *
   * <p>대표 이미지({@link #firstImageUrl})가 비어 있으면 첫 장으로 채운다. 이미 있으면 건드리지 않는다 — 고캠핑에서 받은 대표 이미지를 덮어쓰지 않기
   * 위해서다.
   */
  public void attachImages(List<Image> images) {
    if (images == null || images.isEmpty()) {
      return;
    }
    this.images.addAll(images);
    if (!StringUtils.hasText(this.firstImageUrl)) {
      this.firstImageUrl = this.images.get(0).getImageUrl();
    }
  }

  /**
   * 이미지를 전달받은 목록으로 통째로 교체하고, 이번 교체로 떨어져 나간 이미지들을 돌려준다. 반환된 이미지는 이 캠핑장에서 완전히 빠진 것들이라, 호출측이 images 행과
   * 저장소 객체를 정리하는 근거로 쓴다.
   *
   * <p>비교는 Image 인스턴스 동일성 기준이다. 남기는 이미지는 이미 이 컬렉션에 로딩된 바로 그 객체를 다시 넘겨야 한다.
   *
   * <p>대표 이미지는 새 첫 장으로 다시 맞춘다. 목록이 비면 대표도 비운다 — 지운 이미지의 URL 이 대표로 남아 깨진 링크가 되는 것을 막는다.
   */
  public List<Image> replaceImages(List<Image> newImages) {
    List<Image> detached = new ArrayList<>(this.images);
    detached.removeAll(newImages);

    this.images.clear();
    this.images.addAll(newImages);
    this.firstImageUrl = this.images.isEmpty() ? null : this.images.get(0).getImageUrl();
    this.updatedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));

    return detached;
  }

  // average_rating은 엔티티에서 직접 바꾸지 않는다. 동시 갱신 시 마지막 커밋이 옛 평균으로 덮어쓰는 걸 막으려고
  // CampRepository.refreshAverageRating(campId)의 UPDATE 한 문장으로만 갱신한다.

  // 주소 변경에 따른 좌표 갱신. 지오코딩 실패(geoPoint == null) 시 좌표를 비워서
  // 새 주소와 옛 좌표가 어긋난 채로 저장되지 않도록 한다.
  public void updateLocation(GeoPoint geoPoint) {
    this.mapX = geoPoint != null ? geoPoint.mapX() : null;
    this.mapY = geoPoint != null ? geoPoint.mapY() : null;
  }

  // price: 고캠핑 API가 가격 정보를 제공하지 않아, 서비스 계층에서 정책에 따라 결정한 값을 받아 조립만 한다.
  public static Camp fromGocampingApi(
      com.basecamp.backend.domain.camp.dto.request.GocampingApiResponseDto dto, int price) {
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
        .manageSttus(CampManageStatus.fromLabel(dto.getManageSttus()))
        .lineIntro(truncate(dto.getIntro(), 500))
        .homepage(truncate(dto.getHomepage(), 255))
        .sbrsCl(truncate(dto.getSbrsCl(), 500))
        .price(price)
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

  // 캠핑장 소유권 검증 메서드
  public void validateOwner(Long userId) {
    if (!Objects.equals(this.ownerId, userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
  }
}
