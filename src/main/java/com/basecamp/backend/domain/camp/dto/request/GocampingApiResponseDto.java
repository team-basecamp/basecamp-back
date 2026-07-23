package com.basecamp.backend.domain.camp.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

// gocamping 공공데이터 API의 응답 데이터를 받기 위한 DTO
// 실제 API 응답에는 여기 매핑되지 않은 필드가 훨씬 더 많으므로, 알 수 없는 필드는 무시한다
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GocampingApiResponseDto {

  // 캠핑장 고유 ID
  @NotNull
  @JsonProperty("contentId")
  private Long contentId;

  // 캠핑장 이름
  @NotBlank
  @JsonProperty("facltNm")
  private String facltNm;

  // 도로명/지번 주소 ( 기본 주소 )
  @JsonProperty("addr1")
  private String addr1;

  // 경도
  @JsonProperty("mapX")
  private Double mapX;

  // 위도
  @JsonProperty("mapY")
  private Double mapY;

  // 캠핑장 연락처
  @JsonProperty("tel")
  private String tel;

  // 캠핑장 종류 시설 유형
  @JsonProperty("induty")
  private String induty;

  // 일반 야영장 사이트 갯수
  @JsonProperty("gnrlSiteCo")
  private Integer gnrlSiteCo;

  // 오토 캠핑 사이트 갯수
  @JsonProperty("autoSiteCo")
  private Integer autoSiteCo;

  // 글램핑 사이트 갯수
  @JsonProperty("glampSiteCo")
  private Integer glampSiteCo;

  // 캠핑장 대표 이미지 URL
  @JsonProperty("firstImageUrl")
  private String firstImageUrl;

  // 캠핑장 운영 상태 ( 운영 , 휴장 ,폐장 )
  @JsonProperty("manageSttus")
  private String manageSttus;

  // 캠핑장 소개글
  @JsonProperty("intro")
  private String intro;

  // 웹사이트
  @JsonProperty("homepage")
  private String homepage;

  // 부대시설 (편의시설, 콤마 구분 문자열)
  @JsonProperty("sbrsCl")
  private String sbrsCl;

  // 운영 시작일 (YYYYMMDD)
  @JsonProperty("hvofBgnde")
  private String hvofBgnde;

  // 운영 종료일 (YYYYMMDD)
  @JsonProperty("hvofEndde")
  private String hvofEndde;

  @Override
  public String toString() {
    return "GocampingApiResponseDto{"
        + "contentId="
        + contentId
        + ", facltNm='"
        + facltNm
        + '\''
        + ", addr1='"
        + addr1
        + '\''
        + ", mapX="
        + mapX
        + ", mapY="
        + mapY
        + ", tel='"
        + tel
        + '\''
        + ", induty='"
        + induty
        + '\''
        + ", gnrlSiteCo="
        + gnrlSiteCo
        + ", autoSiteCo="
        + autoSiteCo
        + ", glampSiteCo="
        + glampSiteCo
        + ", firstImageUrl='"
        + firstImageUrl
        + '\''
        + ", manageSttus='"
        + manageSttus
        + '\''
        + ", intro='"
        + intro
        + '\''
        + ", homepage='"
        + homepage
        + '\''
        + ", sbrsCl='"
        + sbrsCl
        + '\''
        + ", hvofBgnde='"
        + hvofBgnde
        + '\''
        + ", hvofEndde='"
        + hvofEndde
        + '\''
        + '}';
  }
}
