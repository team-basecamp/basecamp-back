package com.basecamp.backend.common.storage;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 저장 추상화. 도메인 서비스는 이 인터페이스에만 의존하고, 실제 저장 위치는 구현체가 감춘다.
 *
 * <p>반환하는 {@link StoredObject}는 공개 URL과 객체 키를 함께 담는다. URL은 DB/응답에 그대로 싣고, 키는 삭제할 때 쓴다.
 */
public interface FileStorageService {

  /**
   * 단일 파일을 저장하고 공개 URL과 객체 키를 반환한다.
   *
   * @param category 저장 위치를 나누는 용도 구분
   * @throws com.basecamp.backend.common.exception.BusinessException 형식이 허용되지 않거나 저장에 실패한 경우
   */
  StoredObject store(MultipartFile file, ImageCategory category);

  /**
   * 여러 파일을 저장하고 저장 순서대로 반환한다. {@code null}/빈 파일은 건너뛰며, 개수 상한을 넘으면 저장 없이 예외로 막는다. 도중에 하나라도 실패하면 이미
   * 올린 것을 되돌려 고아 객체를 남기지 않는다.
   *
   * @return 저장된 객체 목록(입력이 비어 있으면 빈 목록)
   */
  List<StoredObject> storeAll(List<MultipartFile> files, ImageCategory category);

  /**
   * 공개 URL로 저장 객체를 삭제한다.
   *
   * <p>이 저장소가 발급하지 않은 URL(소셜 로그인이 준 외부 프로필 이미지 등)이거나 값이 비어 있으면 조용히 무시한다. 같은 컬럼에 외부 URL과 저장소 URL이 섞여
   * 있어도 안전하게 호출할 수 있도록 한 계약이다.
   */
  void delete(String imageUrl);
}
