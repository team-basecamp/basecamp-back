package com.basecamp.backend.common.storage;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일(이미지) 저장 추상화. 도메인 서비스는 이 인터페이스에만 의존하고,
 * 실제 저장 위치(로컬 디스크 등)는 구현체가 감춘다. 반환값은 언제나 DB/응답에 그대로 쓸 수 있는
 * <b>상대경로</b>(예: {@code /images/abc123.jpg})다.
 *
 * <p>게시글 작성에서 먼저 쓰이지만, 리뷰 이미지 등 다른 도메인도 동일하게 재사용하도록 도메인 중립으로 둔다.
 * 삭제/수정 기능이 붙을 때를 대비해 {@link #delete(String)} 도 미리 노출한다.</p>
 */
public interface FileStorageService {

	/**
	 * 단일 파일을 저장하고 접근용 상대경로를 반환한다.
	 * @throws com.basecamp.backend.common.exception.BusinessException 형식이 허용되지 않거나 저장에 실패한 경우
	 */
	String store(MultipartFile file);

	/**
	 * 여러 파일을 저장하고 저장 순서대로 상대경로 목록을 반환한다.
	 * {@code null}/빈 파일은 건너뛰며, 개수 상한을 넘으면 저장 없이 예외로 막는다.
	 * @return 저장된 파일들의 상대경로(입력이 비어 있으면 빈 목록)
	 */
	List<String> storeAll(List<MultipartFile> files);

	/**
	 * 상대경로로 저장 파일을 삭제한다. 경로가 비었거나 관리 대상이 아니면 조용히 무시한다.
	 * (게시글 삭제/수정 기능에서 사용할 확장 지점)
	 */
	void delete(String relativePath);
}
