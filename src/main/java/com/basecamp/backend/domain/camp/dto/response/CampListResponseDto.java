package com.basecamp.backend.domain.camp.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// 캠핑장 목록/검색/HOT 조회 공통 응답 envelope
@Getter
@Builder
public class CampListResponseDto {

    private String resultCode;
    private String resultMsg;
    private List<CampResponseDto> data;
    private long totalCount;

    public static CampListResponseDto ok(List<CampResponseDto> data, long totalCount) {
        return CampListResponseDto.builder()
                .resultCode("0000")
                .resultMsg("OK")
                .data(data)
                .totalCount(totalCount)
                .build();
    }
}
