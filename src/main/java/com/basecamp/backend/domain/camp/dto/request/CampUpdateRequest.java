package com.basecamp.backend.domain.camp.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampUpdateRequest {
  /*
   * 선택 가능한 필드 (모두 optional — 값이 있으면 검증, 없으면 기존값 유지)
   */
  @Size(min = 2, max = 100, message = "시설명은 최소 2자 최대 100자 입니다")
  private String facltNm;

  @Size(max = 200, message = "주소는 최대 200자 내로 적어주세요")
  private String addr1;

  // Pattern 정규식 적용 : 전화번호는 010,011,033,02 등 지역 번호에 맞게 저장을 할수 있도록 정규식을 적용하였습니다.
  // pattern 정규 표현식을 사용하여서 패턴을 검사를 하도록 설정
  @Pattern(
      regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$|^\\d{2,3}-\\d{3,4}-\\d{4}$",
      message = "전화번호는 양식에 맞게 설정 부탁드립니다")
  private String tel;

  @Size(max = 100, message = "유형은 최대 100글자 입니다")
  private String induty;

  @Min(value = 0, message = "가격은 최소 0원 이상입니다.")
  private Integer price;

  /* 선택 가능한 필드 (Optional) */
  @Size(max = 200, message = "상세주소는 최대 200자 입니다")
  private String addr2;

  @Min(value = 0, message = "사이트 수는 최소 0이상입니다")
  @Max(value = 10000, message = "사이트 수는 최대 10000이하 입니다")
  private Integer gnrlSiteCo;

  @Min(value = 0, message = "사이트 수는 최소 0이상입니다")
  @Max(value = 10000, message = "사이트 수는 최대 10000이하 입니다")
  private Integer autoSiteCo;

  @Min(value = 0, message = "사이트 수는 최소 0이상입니다")
  @Max(value = 10000, message = "사이트 수는 최대 10000이하 입니다")
  private Integer glampSiteCo;

  // 한줄 소개
  @Size(max = 500, message = "한줄 소개는 최대 500글자")
  private String lineIntro;

  // 대표 이미지 URL
  @Size(max = 255, message = "대표이미지 URL은 최대 255글자")
  private String firstImageUrl;

  // 캠핑장 웹사이트
  @URL(regexp = "^https?://.*", message = "웹사이트 URL은 http 또는 https로 시작해야 합니다")
  @Size(max = 255, message = "웹사이트 URL은 최대 255글자")
  private String homepage;

  /**
   * 남길 기존 이미지의 URL 목록. 새로 올리는 파일과 합쳐 최종 이미지 목록이 된다.
   *
   * <ul>
   *   <li>{@code null} — 필드를 안 보낸 것. 이미지는 건드리지 않는다(기존 전부 유지).
   *   <li>빈 목록 — 기존 이미지를 전부 지우겠다는 뜻.
   * </ul>
   *
   * <p>여기 담긴 URL 은 반드시 이 캠핑장에 현재 붙어 있는 것이어야 한다. 남의 캠핑장 이미지 URL 을 실어 가져오는 것을 막기 위해 서버에서 되짚어 검증한다.
   */
  private List<String> keepImageUrls;
}
