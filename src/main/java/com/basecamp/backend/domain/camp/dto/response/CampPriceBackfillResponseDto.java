package com.basecamp.backend.domain.camp.dto.response;

import lombok.Builder;
import lombok.Getter;

// POST /api/v1/camps/backfill-price 응답. updatedCount를 필드로 노출해 클라이언트가 문자열을 파싱하지 않게 한다.
@Getter
@Builder
public class CampPriceBackfillResponseDto {

  private int updatedCount;
  private String message;

  public static CampPriceBackfillResponseDto of(int updatedCount) {
    return CampPriceBackfillResponseDto.builder()
        .updatedCount(updatedCount)
        .message(updatedCount + "개 캠핑장의 가격이 채워졌습니다")
        .build();
  }
}
