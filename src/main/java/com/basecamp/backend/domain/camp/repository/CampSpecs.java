package com.basecamp.backend.domain.camp.repository;

import com.basecamp.backend.domain.camp.entity.Camp;
import org.springframework.data.jpa.domain.Specification;

// 캠핑장 검색 조건(키워드/지역/유형/가격)을 조합하기 위한 Specification 모음
public class CampSpecs {

    private CampSpecs() {
    }

    public static Specification<Camp> isOperating() {
        return (root, query, cb) -> cb.equal(root.get("manageSttus"), "운영");
    }

    public static Specification<Camp> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String like = "%" + keyword.trim() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(root.get("facltNm"), like),
                cb.like(root.get("addr1"), like)
        );
    }

    public static Specification<Camp> regionContains(String region) {
        if (region == null || region.isBlank() || "전체".equals(region)) return null;
        String like = "%" + region.trim() + "%";
        return (root, query, cb) -> cb.like(root.get("addr1"), like);
    }

    public static Specification<Camp> indutyContains(String induty) {
        if (induty == null || induty.isBlank() || "전체".equals(induty)) return null;
        String like = "%" + induty.trim() + "%";
        return (root, query, cb) -> cb.like(root.get("induty"), like);
    }

    public static Specification<Camp> priceLessThanOrEqual(Integer priceMax) {
        if (priceMax == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), priceMax);
    }
}
