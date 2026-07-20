package com.basecamp.backend.domain.camp.dto.response;

import lombok.Builder;
import lombok.Getter;

// 캠핑장 단건(상세) 조회 공통 응답 envelope
@Getter
@Builder
public class CampDetailResponseDto {

  private String resultCode;
  private String resultMsg;
  private CampResponseDto data;

  public static CampDetailResponseDto ok(CampResponseDto data) {
    return CampDetailResponseDto.builder().resultCode("0000").resultMsg("OK").data(data).build();
  }
}
