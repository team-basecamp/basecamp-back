package com.basecamp.backend.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 삭제 = 휴지통 이동 동작 검증. 실제 파일시스템(@TempDir)에 대고 확인한다.
 * 저장(store) 경로는 MultipartFile·ImageIO 가 얽혀 있어 여기서는 다루지 않고, 삭제 수명 주기만 본다.
 */
class LocalFileStorageServiceTest {

	@TempDir
	Path root;

	private FileStorageProperties properties;
	private LocalFileStorageService service;

	private Path uploadDir;
	private Path trashDir;

	@BeforeEach
	void setUp() {
		uploadDir = root.resolve("uploads/images");
		trashDir = root.resolve("uploads/trash");

		properties = new FileStorageProperties();
		properties.setDir(uploadDir.toString());
		properties.setTrashDir(trashDir.toString());
		properties.setUrlPrefix("/images");

		service = new LocalFileStorageService(properties);
	}

	// 업로드 디렉터리에 저장된 것처럼 파일 하나를 만들고 그 상대경로를 돌려준다.
	private String givenStoredFile(String fileName) throws IOException {
		Files.createDirectories(uploadDir);
		Files.writeString(uploadDir.resolve(fileName), "image-bytes");
		return "/images/" + fileName;
	}

	@Test
	void delete_저장된파일이면_지우지않고_휴지통으로_옮긴다() throws IOException {
		// given
		String relativePath = givenStoredFile("a3f9c1.jpg");

		// when
		service.delete(relativePath);

		// then — 서빙 위치에서는 사라지고, 실물은 휴지통에 남아 되살릴 수 있다.
		assertThat(uploadDir.resolve("a3f9c1.jpg")).doesNotExist();
		assertThat(trashDir.resolve("a3f9c1.jpg")).exists().hasContent("image-bytes");
	}

	@Test
	void delete_이미없는파일이면_조용히_넘어간다() {
		// given — 원본이 없는 경로 (중복 삭제 등)

		// when & then
		assertThatCode(() -> service.delete("/images/없는파일.jpg")).doesNotThrowAnyException();
	}

	@Test
	void delete_관리대상이아닌경로면_아무것도하지않는다() throws IOException {
		// given — url-prefix 로 시작하지 않는 경로(더미 이미지 등)
		Files.createDirectories(trashDir);

		// when
		service.delete("/static/dummy.jpg");

		// then — 휴지통에 아무것도 들어오지 않는다.
		assertThat(Files.list(trashDir)).isEmpty();
	}

	@Test
	void 기동검증_휴지통이_업로드디렉터리_안에있으면_기동에_실패한다() {
		// given — 휴지통이 서빙 대상 안이면 삭제된 이미지가 URL로 계속 열린다.
		properties.setTrashDir(uploadDir.resolve("trash").toString());

		// when & then
		assertThatThrownBy(() -> service.validateTrashIsNotServed())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("file.upload.trash-dir");
	}

	@Test
	void 기동검증_휴지통이_업로드디렉터리_바깥이면_통과한다() {
		// given — 기본 설정(uploads/images, uploads/trash)

		// when & then
		assertThatCode(() -> service.validateTrashIsNotServed()).doesNotThrowAnyException();
	}
}
