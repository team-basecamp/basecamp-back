package com.basecamp.backend.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 디스크에 이미지를 저장하는 {@link FileStorageService} 구현.
 *
 * <p>파일명은 원본을 신뢰하지 않고 UUID로 새로 만들어 경로 조작·한글/공백·중복 충돌을 원천 차단한다.
 * 저장 위치는 {@link FileStorageProperties#getDir()}(작업 디렉터리 기준 상대경로)이며,
 * DB/응답에는 {@link FileStorageProperties#getUrlPrefix()} 로 시작하는 상대경로만 돌려준다.</p>
 *
 * <p>삭제는 즉시 파일을 지우지 않고 {@link FileStorageProperties#getTrashDir()} 로 옮기는 소프트 삭제다.
 * 휴지통은 서빙 대상 밖이라 URL로 열리지 않으면서 실물은 남아 있어, 실수로 지운 사진을 되살릴 수 있다.</p>
 *
 * <p>휴지통은 <b>자동으로 비워지지 않는다.</b> 파일이 계속 쌓이므로 디스크가 찰 즈음에는 운영자가 직접
 * 비워야 한다. 자동 청소가 필요해지면 보관 기한을 정해 배치를 붙이는 것이 다음 단계다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

	private final FileStorageProperties properties;

	@Override
	public String store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			// 빈 파트가 넘어오면 저장할 것이 없으므로 잘못된 입력으로 막는다.
			throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
		}

		String extension = resolveExtension(file);
		String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

		Path baseDir = baseDir();
		Path target = baseDir.resolve(storedName).normalize();
		// UUID 파일명이라 정상 경로지만, 저장 루트를 벗어나지 않는지 방어적으로 한 번 더 확인한다.
		if (!target.startsWith(baseDir)) {
			throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
		}

		try {
			Files.createDirectories(baseDir);
			try (InputStream in = file.getInputStream()) {
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			log.error("이미지 저장 실패: {}", target, e);
			throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
		}

		// DB에는 절대경로가 아닌 상대경로만 남긴다. (예: /images/abc123.jpg)
		return properties.getUrlPrefixPath() + storedName;
	}

	@Override
	public List<String> storeAll(List<MultipartFile> files) {
		if (files == null || files.isEmpty()) {
			return List.of();
		}

		// 프론트가 빈 파트를 함께 보내는 경우가 있어, 실제 내용이 있는 파일만 남긴다.
		List<MultipartFile> nonEmpty = files.stream()
				.filter(f -> f != null && !f.isEmpty())
				.toList();

		if (nonEmpty.isEmpty()) {
			return List.of();
		}
		if (nonEmpty.size() > properties.getMaxCount()) {
			throw new BusinessException(ErrorCode.IMAGE_COUNT_EXCEEDED);
		}

		// 여러 파일을 하나라도 실패하면 전부 실패로 본다. 도중에 실패하면 이미 저장한 파일을
		// 그대로 두지 않고 되돌려(delete), 고아 파일이 디스크에 남지 않게 한다.
		List<String> stored = new ArrayList<>(nonEmpty.size());
		try {
			for (MultipartFile file : nonEmpty) {
				stored.add(store(file));
			}
		} catch (RuntimeException e) {
			// 정리 실패가 원래 실패 원인을 가리지 않도록 best-effort 로 되돌린다.
			for (String path : stored) {
				try {
					delete(path);
				} catch (RuntimeException cleanupError) {
					log.warn("업로드 롤백 중 이미지 삭제 실패: {}", path, cleanupError);
				}
			}
			throw e;
		}
		return List.copyOf(stored);
	}

	/**
	 * 삭제 = 휴지통으로 이동. 파일을 지우지 않고 서빙되지 않는 휴지통 디렉터리로 옮긴다.
	 * 저장 파일명이 UUID라 휴지통 안에서도 이름이 겹치지 않는다.
	 */
	@Override
	public void delete(String relativePath) {
		if (relativePath == null || relativePath.isBlank()) {
			return;
		}
		String prefix = properties.getUrlPrefixPath();
		if (!relativePath.startsWith(prefix)) {
			// 우리가 저장한 형식의 경로가 아니면(더미 이미지 등) 삭제 대상이 아니다.
			return;
		}

		String fileName = relativePath.substring(prefix.length());

		Path baseDir = baseDir();
		Path target = baseDir.resolve(fileName).normalize();
		if (!target.startsWith(baseDir)) {
			// 경로 조작 방어: 저장 루트 밖은 건드리지 않는다.
			return;
		}

		Path trashDir = trashDir();
		Path trashTarget = trashDir.resolve(fileName).normalize();
		if (!trashTarget.startsWith(trashDir)) {
			// 같은 이유로 휴지통 루트 밖으로 나가는 이동도 막는다.
			return;
		}

		try {
			Files.createDirectories(trashDir);
			// 원본이 이미 없으면 (중복 삭제 등) 옮길 것도 없다. deleteIfExists 와 같은 관용을 유지한다.
			// REPLACE_EXISTING: UUID 파일명이라 휴지통에서 이름이 겹칠 일이 없지만, 겹친다면 같은 파일이므로 덮어써도 잃는 게 없다.
			Files.move(target, trashTarget, StandardCopyOption.REPLACE_EXISTING);
		} catch (NoSuchFileException e) {
			return;
		} catch (IOException e) {
			log.error("이미지 휴지통 이동 실패: {} -> {}", target, trashTarget, e);
			throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
		}
	}

	/**
	 * 휴지통이 서빙 대상 디렉터리 안에 있으면 "삭제한" 사진이 URL로 계속 열려 휴지통의 의미가 없어진다.
	 * 설정 실수는 조용히 넘어가면 아무도 눈치채지 못하므로 기동 자체를 실패시킨다.
	 */
	@PostConstruct
	void validateTrashIsNotServed() {
		Path baseDir = baseDir();
		Path trashDir = trashDir();

		if (trashDir.startsWith(baseDir) || baseDir.startsWith(trashDir)) {
			throw new IllegalStateException(
					"file.upload.trash-dir 는 file.upload.dir 바깥이어야 합니다. "
							+ "휴지통이 업로드 디렉터리 안에 있으면 삭제된 이미지가 " + properties.getUrlPrefixPath()
							+ " 로 계속 공개됩니다. (dir=" + baseDir + ", trash-dir=" + trashDir + ")");
		}
	}

	// 저장 루트를 절대경로로 정규화한다. 경로 조작 검사(startsWith)의 기준이 된다.
	private Path baseDir() {
		return Paths.get(properties.getDir()).toAbsolutePath().normalize();
	}

	// 휴지통 루트를 절대경로로 정규화한다. 서빙되지 않는 위치이며, 기동 시 baseDir 바깥임을 검증한다.
	private Path trashDir() {
		return Paths.get(properties.getTrashDir()).toAbsolutePath().normalize();
	}

	// 원본 파일명의 확장자와 content-type을 함께 검사해 허용 목록 안일 때만 통과시킨다.
	private String resolveExtension(MultipartFile file) {
		String original = file.getOriginalFilename();
		if (original == null || !original.contains(".")) {
			throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
		}
		String extension = original.substring(original.lastIndexOf('.') + 1).toLowerCase();

		String contentType = file.getContentType();
		boolean allowedExtension = properties.getAllowedExtensions().contains(extension);
		boolean imageContentType = contentType != null && contentType.startsWith("image/");
		if (!allowedExtension || !imageContentType) {
			throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
		}

		// 확장자·Content-Type 은 위조가 쉬우므로, 실제 바이트가 디코딩 가능한 이미지인지 한 번 더 확인한다.
		// (예: .txt 를 .jpg 로 바꿔 올린 경우 여기서 걸러진다)
		// 단, JDK 기본 ImageIO 에는 webp 리더가 없어 정상 webp 도 null 이 되므로 이 검사에서만 제외한다.
		if (!"webp".equals(extension)) {
			try (InputStream in = file.getInputStream()) {
				if (ImageIO.read(in) == null) {
					throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
				}
			} catch (IOException e) {
				throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
			}
		}
		return extension;
	}
}
