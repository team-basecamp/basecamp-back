package com.basecamp.backend.common.storage;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 파일(이미지) 업로드 저장 정책. {@code file.upload.*} 프로퍼티로 주입된다.
 *
 * <p>업로드 파일은 런타임에 {@link #dir} 아래로 저장되고, DB/응답에는 절대경로가 아니라
 * {@link #urlPrefix} 로 시작하는 <b>상대경로</b>(예: {@code /images/abc123.jpg})만 남긴다.
 * 같은 {@code urlPrefix} 로 {@code src/main/resources/static/images} 의 더미 이미지도 함께 제공된다.</p>
 *
 * <p>지금은 로컬 디스크 저장({@link LocalFileStorageService})만 쓰지만, 이 정책 값과
 * {@link FileStorageService} 추상화를 통해 추후 저장 위치/백엔드를 바꿔도 도메인 코드는 그대로 둔다.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "file.upload")
public class FileStorageProperties {

	/** 업로드 이미지를 저장할 로컬 디렉터리(작업 디렉터리 기준 상대경로). Git 제외 대상. */
	private String dir = "uploads/images";

	/** 저장 파일에 부여하는 공개 URL 접두어. DB에는 이 접두어로 시작하는 상대경로만 저장한다. */
	private String urlPrefix = "/images";

	/** 한 요청에 첨부할 수 있는 이미지 최대 개수. */
	private int maxCount = 5;

	/** 허용 확장자(소문자, 점 제외). 이 목록 밖이면 저장을 거부한다. */
	private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "gif", "webp");
}
