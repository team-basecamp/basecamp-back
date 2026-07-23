package com.basecamp.backend.domain.camp.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "찜 토글 결과")
public record WishlistToggleResponse(
    @Schema(description = "캠핑장 ID", example = "12") Long campId,
    @Schema(description = "토글 후 찜 상태 (true=찜함, false=해제됨)", example = "true") boolean wished) {
  public static WishlistToggleResponse of(Long campId, boolean wished) {
    return new WishlistToggleResponse(campId, wished);
  }
}
