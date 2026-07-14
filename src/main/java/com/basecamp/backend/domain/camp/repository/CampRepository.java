package com.basecamp.backend.domain.camp.repository;

import com.basecamp.backend.domain.camp.entity.Camp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampRepository extends JpaRepository<Camp,Long>, JpaSpecificationExecutor<Camp> {

    // contentId로 캠핑장 찾기
    Optional<Camp> findByContentId(Long contentId);

    // 소유자(owner_id) 기준으로 등록한 캠핑장 조회, 최근 등록순
    List<Camp> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    // 모든 캠핑장의 contentId 리스트 조회
    @Query("SELECT c.contentId FROM Camp c")
    List<Long> findAllContentIds();

    // 위치 기반 감석 : 경도 , 위도 범위로 캠핑장 찾기
    List<Camp> findByMapXBetweenAndMapYBetween(
            Double minMapX,
            Double maxMapX,
            Double minMapY,
            Double maxMapY
    );

    // 캠핑장 이름으로 검색 ( 부분 일치)
    List<Camp> findByFacltNmContaining(String facltNm);

    // 특정 지역의 캠핑장 검색
    List<Camp> findByAddr1Containing(String addr1);

    //운영 상태로 검색
    List<Camp> findByManageSttus(String manageSttus);

    // "리뷰 많은순" 정렬 전용 조회. Review 엔티티가 아직 없어서 reviews 테이블을 native query로 직접 LEFT JOIN한다.
    // 키워드/지역/유형/최대금액 필터는 CampSpecs와 동일한 조건을 SQL로 재현한 것.
    @Query(
        value = "SELECT c.* FROM camps c " +
                "LEFT JOIN reviews r ON r.camp_id = c.camp_id " +
                "WHERE c.manage_sttus = '운영' " +
                "  AND (:keyword IS NULL OR c.faclt_nm LIKE CONCAT('%', :keyword, '%') OR c.addr1 LIKE CONCAT('%', :keyword, '%')) " +
                "  AND (:region IS NULL OR c.addr1 LIKE CONCAT('%', :region, '%')) " +
                "  AND (:induty IS NULL OR c.induty LIKE CONCAT('%', :induty, '%')) " +
                "  AND (:priceMax IS NULL OR c.price <= :priceMax) " +
                "GROUP BY c.camp_id " +
                "ORDER BY COUNT(r.review_id) DESC",
        countQuery = "SELECT COUNT(*) FROM (" +
                "  SELECT c.camp_id FROM camps c " +
                "  WHERE c.manage_sttus = '운영' " +
                "    AND (:keyword IS NULL OR c.faclt_nm LIKE CONCAT('%', :keyword, '%') OR c.addr1 LIKE CONCAT('%', :keyword, '%')) " +
                "    AND (:region IS NULL OR c.addr1 LIKE CONCAT('%', :region, '%')) " +
                "    AND (:induty IS NULL OR c.induty LIKE CONCAT('%', :induty, '%')) " +
                "    AND (:priceMax IS NULL OR c.price <= :priceMax)" +
                ") t",
        nativeQuery = true
    )
    Page<Camp> searchOrderByReviewCountDesc(
            @Param("keyword") String keyword,
            @Param("region") String region,
            @Param("induty") String induty,
            @Param("priceMax") Integer priceMax,
            Pageable pageable
    );

}
