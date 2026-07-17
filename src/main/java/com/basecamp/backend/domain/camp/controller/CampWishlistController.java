package com.basecamp.backend.domain.camp.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.camp.dto.response.WishlistResponse;
import com.basecamp.backend.domain.camp.dto.response.WishlistToggleResponse;
import com.basecamp.backend.domain.camp.service.CampWishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "찜(위시리스트)", description = "캠핑장 찜 등록/해제 및 조회 API")
@RestController
@RequiredArgsConstructor
public class CampWishlistController {

    private final CampWishlistService wishlistService;

    @Operation(summary = "찜 토글", description = "이미 찜한 캠핑장이면 해제하고, 아니면 등록합니다.")
    @PostMapping("/api/v1/camps/{campId}/wishlist")
    public ResponseEntity<WishlistToggleResponse> toggleWishlist(
            @AuthenticationPrincipal AuthUser user,
            @PathVariable Long campId) {

        return ResponseEntity.ok(wishlistService.toggleWishlist(user.id(), campId));
    }

    @Operation(summary = "내 찜 목록 조회", description = "로그인한 회원이 찜한 캠핑장 목록을 최신순으로 조회합니다.")
    @GetMapping("/api/v1/wishlists/me")
    public ResponseEntity<List<WishlistResponse>> getMyWishlists(
            @AuthenticationPrincipal AuthUser user) {

        return ResponseEntity.ok(wishlistService.getMyWishlists(user.id()));
    }
}