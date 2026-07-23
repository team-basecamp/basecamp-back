package com.basecamp.backend.domain.camp.repository;

import com.basecamp.backend.domain.camp.entity.CampWishlist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampWishlistRepository extends JpaRepository<CampWishlist, Long> {

  /** 찜 존재 여부 확인 (토글 분기용) */
  boolean existsByUserIdAndCampCampId(Long userId, Long campId);

  /** 찜 단건 조회 (토글 해제 시 삭제 대상) */
  Optional<CampWishlist> findByUserIdAndCampCampId(Long userId, Long campId);

  /** 내 찜 목록 — camp를 fetch join 해서 N+1 방지 */
  @Query(
      "SELECT w FROM CampWishlist w "
          + "JOIN FETCH w.camp "
          + "WHERE w.userId = :userId "
          + "ORDER BY w.createdAt DESC")
  List<CampWishlist> findAllByUserIdWithCamp(@Param("userId") Long userId);
}
