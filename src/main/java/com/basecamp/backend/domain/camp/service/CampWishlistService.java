package com.basecamp.backend.domain.camp.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.dto.response.WishlistResponse;
import com.basecamp.backend.domain.camp.dto.response.WishlistToggleResponse;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.entity.CampWishlist;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.camp.repository.CampWishlistRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampWishlistService {

  /** camp_wishlists 의 복합 UNIQUE 제약 이름 (V1__init_schema.sql) */
  private static final String UQ_WISHLIST = "uq_wishlist";

  private final CampWishlistRepository wishlistRepository;
  private final CampRepository campRepository;

  @Transactional
  public WishlistToggleResponse toggleWishlist(Long userId, Long campId) {
    Camp camp =
        campRepository
            .findById(campId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND));

    boolean alreadyWished = wishlistRepository.existsByUserIdAndCampCampId(userId, campId);

    if (alreadyWished) {
      wishlistRepository
          .findByUserIdAndCampCampId(userId, campId)
          .ifPresent(wishlistRepository::delete);

      log.info("찜 해제 - userId: {}, campId: {}", userId, campId);
      return WishlistToggleResponse.of(campId, false);
    }

    CampWishlist wishlist = CampWishlist.builder().userId(userId).camp(camp).build();

    try {
      wishlistRepository.saveAndFlush(wishlist);
    } catch (DataIntegrityViolationException e) {
      // UNIQUE(user_id, camp_id) 위반만 중복 찜으로 본다.
      // FK/NOT NULL 등 다른 제약 위반은 실제 결함이므로 그대로 던져 500 으로 드러낸다.
      if (!isWishlistDuplicate(e)) {
        throw e;
      }
      log.warn("찜 중복 요청 - userId: {}, campId: {}", userId, campId);
      throw new BusinessException(ErrorCode.WISHLIST_ALREADY_EXISTS);
    }

    log.info("찜 등록 - userId: {}, campId: {}", userId, campId);
    return WishlistToggleResponse.of(campId, true);
  }

  /** 예외 원인이 위시리스트 복합 UNIQUE 위반인지 판별 MySQL 이 제약 이름을 "테이블명.제약명" 형태로 돌려주기도 해 contains 로 비교한다. */
  private boolean isWishlistDuplicate(DataIntegrityViolationException e) {
    if (e.getCause() instanceof ConstraintViolationException cve) {
      String name = cve.getConstraintName();
      return name != null && name.toLowerCase().contains(UQ_WISHLIST);
    }
    return false;
  }

  /** 내 찜 목록 조회. camp 를 fetch join 으로 함께 조회해 N+1 을 방지한다. */
  @Transactional(readOnly = true)
  public List<WishlistResponse> getMyWishlists(Long userId) {
    return wishlistRepository.findAllByUserIdWithCamp(userId).stream()
        .map(WishlistResponse::from)
        .toList();
  }
}
