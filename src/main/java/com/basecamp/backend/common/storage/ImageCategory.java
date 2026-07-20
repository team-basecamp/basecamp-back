package com.basecamp.backend.common.storage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이미지 용도. 버킷 하나 안에서 객체 키 접두어로 쓰인다(예: {@code posts/ab12….jpg}).
 *
 * <p>버킷을 용도마다 쪼개지 않고 접두어로 나눈 이유는, 공개 정책·수명주기 설정을 한 곳에서 관리하면서도 콘솔에서 폴더처럼 구분해 볼 수 있기 때문이다.
 */
@Getter
@RequiredArgsConstructor
public enum ImageCategory {

  /** 회원 프로필 이미지. 소셜 로그인이 준 외부 URL은 저장소를 거치지 않으므로 여기 해당하지 않는다. */
  PROFILE("profiles"),

  /** 게시글 첨부 이미지. */
  POST("posts"),

  /** 리뷰 첨부 이미지. */
  REVIEW("reviews"),

  /** 직접 등록한 캠핑장의 이미지. 고캠핑 API가 준 외부 이미지 URL은 해당하지 않는다. */
  CAMP("camps");

  private final String prefix;

  /** 파일명 앞에 용도 접두어를 붙여 객체 키를 만든다. */
  public String objectKey(String fileName) {
    return prefix + "/" + fileName;
  }
}
