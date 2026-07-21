package com.basecamp.backend.domain.camp.repository;

import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.entity.CampManageStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CampRepository extends JpaRepository<Camp, Long>, JpaSpecificationExecutor<Camp> {

  // 리뷰 변경 후 캐싱 컬럼(camps.average_rating)을 리뷰 전체 재집계로 다시 채운다.
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
            update camps c
            set c.average_rating = (
                select round(coalesce(avg(rv.rating), 0.0), 1)
                from reviews rv
                where rv.camp_id = c.camp_id
            )
            where c.camp_id = :campId
            """,
      nativeQuery = true)
  void refreshAverageRating(@Param("campId") Long campId);

  // contentId로 캠핑장 찾기
  Optional<Camp> findByContentId(Long contentId);

  // 상세 조회 전용. 이미지까지 함께 로딩한다.
  //
  // 캠핑장은 서비스가 엔티티를 그대로 반환하고 컨트롤러에서 DTO 로 바꾸는데, open-in-view: false 라
  // 그 시점에는 트랜잭션이 닫혀 있다. 지연 로딩 그대로 두면 갤러리를 읽는 순간 예외가 난다.
  // 그래서 상세 경로에서만 EntityGraph 로 미리 초기화해 둔다.
  //
  // 목록 경로에는 쓰지 않는다 — 캠핑장마다 이미지를 끌고 오면 N+1 이 되고, 목록은 대표 이미지
  // (camps.first_image_url) 한 장만 있으면 되기 때문이다.
  @EntityGraph(attributePaths = "images")
  Optional<Camp> findWithImagesByCampId(Long campId);

  @EntityGraph(attributePaths = "images")
  Optional<Camp> findWithImagesByContentId(Long contentId);

  // 사업자 대시보드 통계: 보유 캠핑장 전체(각 캠핑장의 캐싱된 average_rating)의 평균.
  // 리뷰 건수가 많은 캠핑장 쪽으로 쏠리지 않도록 캠핑장마다 동일한 가중치를 준다.
  // 소유 캠핑장이 없으면(=owner_id 매칭 0건) null이 반환된다.
  @Query("select avg(c.averageRating) from Camp c where c.ownerId = :ownerId")
  Double findAverageRatingAcrossOwnedCamps(@Param("ownerId") Long ownerId);

  // 소유자(owner_id) 기준으로 등록한 캠핑장 조회, 최근 등록순
  List<Camp> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

  // 모든 캠핑장의 contentId 리스트 조회
  @Query("SELECT c.contentId FROM Camp c")
  List<Long> findAllContentIds();

  // 위치 기반 감석 : 경도 , 위도 범위로 캠핑장 찾기
  List<Camp> findByMapXBetweenAndMapYBetween(
      Double minMapX, Double maxMapX, Double minMapY, Double maxMapY);

  // 캠핑장 이름으로 검색 ( 부분 일치)
  List<Camp> findByFacltNmContaining(String facltNm);

  // 특정 지역의 캠핑장 검색
  List<Camp> findByAddr1Containing(String addr1);

  // 운영 상태로 검색
  List<Camp> findByManageSttus(CampManageStatus manageSttus);

  // "리뷰 많은순" 정렬 전용 조회. Review 엔티티가 아직 없어서 reviews 테이블을 native query로 직접 LEFT JOIN한다.
  // 키워드/지역/유형/최대금액 필터는 CampSpecs와 동일한 조건을 SQL로 재현한 것.
  @Query(
      value =
          "SELECT c.* FROM camps c "
              + "LEFT JOIN reviews r ON r.camp_id = c.camp_id "
              + "WHERE c.manage_sttus = '운영' "
              + "  AND c.deleted_at IS NULL "
              + "  AND (:keyword IS NULL OR c.faclt_nm LIKE CONCAT('%', :keyword, '%') OR c.addr1 LIKE CONCAT('%', :keyword, '%')) "
              + "  AND (:region IS NULL OR c.addr1 LIKE CONCAT('%', :region, '%')) "
              + "  AND (:induty IS NULL OR c.induty LIKE CONCAT('%', :induty, '%')) "
              + "  AND (:priceMax IS NULL OR c.price <= :priceMax) "
              + "GROUP BY c.camp_id "
              + "ORDER BY COUNT(r.review_id) DESC",
      countQuery =
          "SELECT COUNT(*) FROM ("
              + "  SELECT c.camp_id FROM camps c "
              + "  WHERE c.manage_sttus = '운영' "
              + "    AND c.deleted_at IS NULL "
              + "    AND (:keyword IS NULL OR c.faclt_nm LIKE CONCAT('%', :keyword, '%') OR c.addr1 LIKE CONCAT('%', :keyword, '%')) "
              + "    AND (:region IS NULL OR c.addr1 LIKE CONCAT('%', :region, '%')) "
              + "    AND (:induty IS NULL OR c.induty LIKE CONCAT('%', :induty, '%')) "
              + "    AND (:priceMax IS NULL OR c.price <= :priceMax)"
              + ") t",
      nativeQuery = true)
  Page<Camp> searchOrderByReviewCountDesc(
      @Param("keyword") String keyword,
      @Param("region") String region,
      @Param("induty") String induty,
      @Param("priceMax") Integer priceMax,
      Pageable pageable);

  // HOT 캠핑장 - 예약건수순 정렬 전용 조회.
  // camps.reservation_count는 갱신 로직이 아직 없어 신뢰할 수 없으므로,
  // reviews와 동일하게 reservations 테이블을 실시간 LEFT JOIN + COUNT로 집계한다.
  // status 조건은 WHERE가 아니라 ON 절에 둬야 한다 — WHERE에 두면 매칭되는
  // 예약이 없는 캠핑장(0건)까지 결과에서 통째로 빠지는 버그가 생긴다(사실상 INNER JOIN이 됨).
  // COUNT(r.reservation_id): LEFT JOIN에서 매칭 안 되면 오른쪽 테이블(r) 컬럼은 전부 NULL이 되고,
  // COUNT는 NULL을 안 세므로 예약 0건인 캠핑장은 정확히 0으로 집계된다.
  @Query(
      value =
          "SELECT c.* FROM camps c "
              + "LEFT JOIN reservations r ON r.camp_id = c.camp_id "
              + "    AND r.status IN (:statuses) "
              + "WHERE c.manage_sttus = '운영' "
              + "  AND c.deleted_at IS NULL "
              + "GROUP BY c.camp_id "
              + "ORDER BY COUNT(r.reservation_id) DESC, c.average_rating DESC, c.camp_id ASC",
      countQuery =
          "SELECT COUNT(*) FROM ("
              + "  SELECT c.camp_id FROM camps c "
              + "  WHERE c.manage_sttus = '운영' AND c.deleted_at IS NULL"
              + ") t",
      nativeQuery = true)
  Page<Camp> findHotCampsByReservationCountDesc(
      @Param("statuses") List<String> statuses, Pageable pageable);
}
