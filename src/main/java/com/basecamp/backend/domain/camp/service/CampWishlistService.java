package com.basecamp.backend.domain.camp.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.dto.response.WishlistResponse;
import com.basecamp.backend.domain.camp.dto.response.WishlistToggleResponse;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.entity.CampWishlist;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.camp.repository.CampWishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;



@Slf4j
@Service
@RequiredArgsConstructor
public class CampWishlistService {

    private final CampWishlistRepository wishlistRepository;
    private final CampRepository campRepository;

    @Transactional
    public WishlistToggleResponse toggleWishlist(Long userId, Long campId) {
        Camp camp = campRepository.findById(campId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND));

        boolean alreadyWished = wishlistRepository.existsByUserIdAndCampCampId(userId, campId);

        if (alreadyWished) {
            wishlistRepository.findByUserIdAndCampCampId(userId, campId)
                    .ifPresent(wishlistRepository::delete);

            log.info("찜 해제 - userId: {}, campId: {}", userId, campId);
            return WishlistToggleResponse.of(campId, false);
        }

        CampWishlist wishlist = CampWishlist.builder()
                .userId(userId)
                .camp(camp)
                .build();

        try {
            wishlistRepository.saveAndFlush(wishlist);
        } catch (DataIntegrityViolationException e) {
            log.warn("찜 중복 요청 - userId: {}, campId: {}", userId, campId);
            throw new BusinessException(ErrorCode.WISHLIST_ALREADY_EXISTS);
        }

        log.info("찜 등록 - userId: {}, campId: {}", userId, campId);
        return WishlistToggleResponse.of(campId, true);
    }

    /**
     * 내 찜 목록 조회. camp를 fetch join으로 함께 조회해 N+1을 방지한다.
     */
    @Transactional(readOnly = true)
    public List<WishlistResponse> getMyWishlists(Long userId) {
        return wishlistRepository.findAllByUserIdWithCamp(userId).stream()
                .map(WishlistResponse::from)
                .toList();
    }
}