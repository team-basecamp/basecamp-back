package com.basecamp.backend.domain.camp.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampRegistrationRequest {

  /*
   * 필수 입력 필드
   */
  @NotBlank(message = "시설명은 필수 입력 값입니다.")
  @Size(min = 2, max = 100, message = "시설명은 최소 2자 최대 100자 입니다")
  private String facltNm;

  @NotBlank(message = "주소는 필수 입력 값입니다")
  @Size(max = 200, message = "주소는 최대 200자 내로 적어주세요")
  private String addr1;

  // Pattern 정규식 적용 : 전화번호는 010,011,033,02 등 지역 번호에 맞게 저장을 할수 있도록 정규식을 적용하였습니다.
  // pattern 정규 표현식을 사용하여서 패턴을 검사를 하도록 설정
  @Pattern(
      regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$|^\\d{2,3}-\\d{3,4}-\\d{4}$",
      message = "전화번호는 양식에 맞게 설정 부탁드립니다")
  @NotBlank(message = "전화번호는 필수 입력 값입니다.")
  private String tel;

  @NotBlank(message = "캠핑장 유형은 필수 입력 값입니다")
  @Size(max = 100, message = "유형은 최대 100글자 입니다")
  private String induty;

  @NotNull(message = "가격은 필수 입력 값입니다")
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
}
