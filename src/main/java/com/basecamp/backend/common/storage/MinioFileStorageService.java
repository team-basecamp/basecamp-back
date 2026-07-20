package com.basecamp.backend.common.storage;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * MinIO에 이미지를 저장하는 {@link FileStorageService} 구현.
 *
 * <p>파일명은 원본을 신뢰하지 않고 UUID로 새로 만든다. 한글·공백·중복 충돌과 키 조작을 원천 차단하기 위해서다. 객체 키는 {@link ImageCategory}
 * 접두어 아래에 놓이고, DB/응답에는 그 키에 대응하는 공개 URL을 돌려준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStorageService implements FileStorageService {

  private final MinioClient minioClient;
  private final MinioProperties properties;

  @Override
  public StoredObject store(MultipartFile file, ImageCategory category) {
    if (file == null || file.isEmpty()) {
      // 빈 파트가 넘어오면 저장할 것이 없으므로 잘못된 입력으로 막는다.
      throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
    }

    String extension = resolveExtension(file);
    String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
    String objectKey = category.objectKey(storedName);

    try (InputStream in = file.getInputStream()) {
      minioClient.putObject(
          PutObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).stream(
                  in, file.getSize(), -1)
              // 브라우저가 다운로드가 아니라 이미지로 렌더링하도록 지정한다.
              .contentType(file.getContentType())
              .build());
    } catch (Exception e) {
      log.error("이미지 업로드 실패. objectKey={}", objectKey, e);
      throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
    }

    return new StoredObject(properties.publicUrl(objectKey), objectKey);
  }

  @Override
  public List<StoredObject> storeAll(List<MultipartFile> files, ImageCategory category) {
    if (files == null || files.isEmpty()) {
      return List.of();
    }

    // 프론트가 빈 파트를 함께 보내는 경우가 있어, 실제 내용이 있는 파일만 남긴다.
    List<MultipartFile> nonEmpty = files.stream().filter(f -> f != null && !f.isEmpty()).toList();

    if (nonEmpty.isEmpty()) {
      return List.of();
    }
    if (nonEmpty.size() > properties.getMaxCount()) {
      throw new BusinessException(ErrorCode.IMAGE_COUNT_EXCEEDED);
    }

    // 여러 파일 중 하나라도 실패하면 전부 실패로 본다. 도중에 실패하면 이미 올린 객체를
    // 그대로 두지 않고 되돌려, 저장소에 고아 객체가 남지 않게 한다.
    List<StoredObject> stored = new ArrayList<>(nonEmpty.size());
    try {
      for (MultipartFile file : nonEmpty) {
        stored.add(store(file, category));
      }
    } catch (RuntimeException e) {
      // 정리 실패가 원래 실패 원인을 가리지 않도록 best-effort 로 되돌린다.
      for (StoredObject object : stored) {
        try {
          removeObject(object.objectKey());
        } catch (RuntimeException cleanupError) {
          log.warn("업로드 롤백 중 이미지 삭제 실패. objectKey={}", object.objectKey(), cleanupError);
        }
      }
      throw e;
    }
    return List.copyOf(stored);
  }

  @Override
  public void deleteByKey(String objectKey) {
    // 외부 URL 만 참조하는 이미지는 키가 없다. 우리 소유가 아니므로 지울 것도 없다.
    if (objectKey == null || objectKey.isBlank()) {
      return;
    }
    removeObject(objectKey);
  }

  private void removeObject(String objectKey) {
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).build());
    } catch (Exception e) {
      log.error("이미지 삭제 실패. objectKey={}", objectKey, e);
      throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
    }
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
