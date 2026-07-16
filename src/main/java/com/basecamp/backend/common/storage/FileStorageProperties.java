package com.basecamp.backend.common.storage;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
@Validated
@ConfigurationProperties(prefix = "file.upload")
public class FileStorageProperties {

	/** 업로드 이미지를 저장할 로컬 디렉터리(작업 디렉터리 기준 상대경로). Git 제외 대상. */
	private String dir = "uploads/images";

	/**
	 * 저장 파일에 부여하는 공개 URL 접두어. DB에는 이 접두어로 시작하는 상대경로만 저장한다.
	 *
	 * <p>{@link #setUrlPrefix}에서 끝 슬래시를 떼어 정규화하고, 아래 제약으로 바인딩 시점에 검증한다.
	 * 반드시 절대경로("/"로 시작)여야 하고 루트("/") 자체는 거부한다 — 루트를 허용하면
	 * {@link #getUrlPrefixPattern()}이 {@code /**}가 되어 SecurityConfig의 공개 GET 규칙이
	 * API 전체를 인증 없이 열어버린다.</p>
	 */
	@NotBlank
	@Pattern(regexp = "/[^\\s/]+(/[^\\s/]+)*", message = "file.upload.url-prefix 는 \"/\" 로 시작하는 절대경로여야 하고 루트(\"/\")일 수 없습니다")
	private String urlPrefix = "/images";

	/** 한 요청에 첨부할 수 있는 이미지 최대 개수. */
	private int maxCount = 5;

	/** 허용 확장자(소문자, 점 제외). 이 목록 밖이면 저장을 거부한다. */
	private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "gif", "webp");

	/**
	 * 접두어를 정규화해 보관한다. 앞뒤 공백과 끝 슬래시를 떼어 항상 "슬래시로 끝나지 않는" 형태로 맞춘다.
	 * ("/images/", "/images//" 모두 "/images") 덕분에 소비자는 매번 슬래시 유무를 따지지 않아도 된다.
	 * 정규화 결과가 비거나 "/" 뿐이면 위 제약에서 바인딩 실패로 걸러진다.
	 */
	public void setUrlPrefix(String urlPrefix) {
		if (urlPrefix == null) {
			this.urlPrefix = null;
			return;
		}
		String trimmed = urlPrefix.trim();
		while (trimmed.length() > 1 && trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		this.urlPrefix = trimmed;
	}

	/** 접두어 아래 리소스를 가리키는 경로 패턴(예: {@code /images/**}). 리소스 핸들러·시큐리티 매처가 함께 쓴다. */
	public String getUrlPrefixPattern() {
		return urlPrefix + "/**";
	}

	/** 접두어 뒤에 파일명을 붙일 때 쓰는 경계(예: {@code /images/}). */
	public String getUrlPrefixPath() {
		return urlPrefix + "/";
	}
}
