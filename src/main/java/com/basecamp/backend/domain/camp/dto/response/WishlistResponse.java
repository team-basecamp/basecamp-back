package com.basecamp.backend.domain.camp.dto.response;

import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.entity.CampWishlist;
import java.time.LocalDateTime;

public record WishlistResponse(
    Long campId, String facltNm, String addr1, String firstImageUrl, LocalDateTime createdAt) {
  public static WishlistResponse from(CampWishlist wishlist) {
    Camp camp = wishlist.getCamp();

    return new WishlistResponse(
        camp.getCampId(),
        camp.getFacltNm(),
        camp.getAddr1(),
        camp.getFirstImageUrl(),
        wishlist.getCreatedAt());
  }
}
