package com.basecamp.backend.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 디스크에 이미지를 저장하는 {@link FileStorageService} 구현.
 *
 * <p>파일명은 원본을 신뢰하지 않고 UUID로 새로 만들어 경로 조작·한글/공백·중복 충돌을 원천 차단한다.
 * 저장 위치는 {@link FileStorageProperties#getDir()}(작업 디렉터리 기준 상대경로)이며,
 * DB/응답에는 {@link FileStorageProperties#getUrlPrefix()} 로 시작하는 상대경로만 돌려준다.</p>
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
		return properties.getUrlPrefix() + "/" + storedName;
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

		return nonEmpty.stream()
				.map(this::store)
				.toList();
	}

	@Override
	public void delete(String relativePath) {
		if (relativePath == null || relativePath.isBlank()) {
			return;
		}
		String prefix = properties.getUrlPrefix() + "/";
		if (!relativePath.startsWith(prefix)) {
			// 우리가 저장한 형식의 경로가 아니면(더미 이미지 등) 삭제 대상이 아니다.
			return;
		}

		Path baseDir = baseDir();
		Path target = baseDir.resolve(relativePath.substring(prefix.length())).normalize();
		if (!target.startsWith(baseDir)) {
			// 경로 조작 방어: 저장 루트 밖은 건드리지 않는다.
			return;
		}

		try {
			Files.deleteIfExists(target);
		} catch (IOException e) {
			log.error("이미지 삭제 실패: {}", target, e);
			throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
		}
	}

	// 저장 루트를 절대경로로 정규화한다. 경로 조작 검사(startsWith)의 기준이 된다.
	private Path baseDir() {
		return Paths.get(properties.getDir()).toAbsolutePath().normalize();
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
		return extension;
	}
}
