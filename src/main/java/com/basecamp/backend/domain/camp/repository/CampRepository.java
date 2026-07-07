package com.basecamp.backend.domain.camp.repository;

import com.basecamp.backend.domain.camp.entity.Camp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampRepository extends JpaRepository<Camp,Long> {

    // contentId로 캠핑장 찾기
    Optional<Camp> findByContentId(Long contentId);

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

}
