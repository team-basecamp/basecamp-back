package com.basecamp.backend.domain.user.entity;

/** 이미지의 저장 방식. 삭제 대상인지 가르는 기준이 된다. */
public enum ImageStorageType {

  /**
   * 외부 URL 참조. 소셜 로그인(카카오/구글/네이버)이 준 프로필 이미지가 여기 해당한다. 우리가 소유한 파일이 아니므로 회원이 이미지를 바꾸거나 지워도 원본을 건드릴 수
   * 없고, 건드려서도 안 된다.
   */
  EXTERNAL,

  /** 우리가 저장소(MinIO)에 올린 이미지. 참조가 끊기면 저장소 객체도 함께 지운다. */
  MINIO
}
