package com.basecamp.backend.domain.user.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.storage.FileStorageService;
import com.basecamp.backend.common.storage.ImageCategory;
import com.basecamp.backend.common.storage.StoredObject;
import com.basecamp.backend.domain.user.dto.request.UpdateProfileRequest;
import com.basecamp.backend.domain.user.dto.response.MyProfileResponse;
import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.ImageRepository;
import com.basecamp.backend.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 회원 본인의 프로필 조회 · 수정.
 *
 * <p>로그인한 회원은 정의상 활성 상태다(탈퇴 · 제재 회원은 access 토큰 인증 자체가 막힌다). 그래도 토큰과 DB 상태가 어긋나는 경우를 대비해 조회 대상이 없으면
 * {@link ErrorCode#USER_NOT_FOUND} 로 방어한다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final ImageRepository imageRepository;
  // 프로필 이미지를 저장소(MinIO)에 올리고 공개 URL·객체 키를 돌려주는 저장 서비스
  private final FileStorageService fileStorageService;

  /** 내 프로필 조회. LAZY 프로필 이미지는 트랜잭션 안에서 매핑한다. */
  @Transactional(readOnly = true)
  public MyProfileResponse getMyProfile(Long userId) {
    return MyProfileResponse.from(findUser(userId));
  }

  /**
   * 내 프로필(닉네임 · 프로필 이미지) 수정.
   *
   * <p>새 이미지 파일이 오면 저장소에 올려 기존 이미지 행을 그대로 재사용해 교체하고(없으면 새로 만든다), 파일 없이 {@code removeImage} 가 참이면
   * 이미지를 제거한다. 둘 다 아니면 이미지는 손대지 않는다.
   *
   * <p>저장소는 트랜잭션에 참여하지 않으므로, 커밋/롤백에 맞춰 실물을 정리하는 훅을 건다. 롤백되면 새로 올린 객체를, 커밋되면 교체·제거로 떨어져 나간 옛 객체를
   * 지운다. 엔티티의 {@code updatedAt} 은 감사(auditing) 리스너가 자동 갱신한다.
   */
  public MyProfileResponse updateMyProfile(
      Long userId, UpdateProfileRequest request, MultipartFile image) {
    User user = findUser(userId);
    Image current = user.getProfileImage();

    // 새 파일이 있으면 저장소에 올린다. 형식 검증·실패는 store 가 담당한다. (없으면 업로드하지 않는다)
    StoredObject stored =
        hasContent(image) ? fileStorageService.store(image, ImageCategory.PROFILE) : null;

    // 커밋/롤백에 맞춰 저장소를 DB 와 일치시킨다. removedKeys 는 아래에서 채워지고, 훅은 완료 시점에 읽는다.
    String storedKey = stored != null ? stored.objectKey() : null;
    List<String> removedKeys = new ArrayList<>();
    registerImageCleanup(storedKey, removedKeys);

    Image profileImage = resolveProfileImage(current, stored, request.removeImage(), removedKeys);
    user.updateProfile(request.nickname(), profileImage);

    // 이미지가 제거된 경우(새 파일 없음 + removeImage): users.image_id 해제만으론 images row 가 고아로 남는다.
    // updateProfile 로 FK(image_id)를 먼저 NULL 로 끊은 뒤 삭제하므로 FK 제약 위반은 없다.
    if (profileImage == null && current != null) {
      imageRepository.delete(current);
      if (current.isStoredByUs()) {
        removedKeys.add(current.getObjectKey()); // 우리 것이면 커밋 후 실물도 지운다
      }
    }
    return MyProfileResponse.from(user);
  }

  /**
   * 최종 프로필 이미지를 정한다. 교체 시 기존 행을 재사용하는데, 그래야 1:1 연관에서 옛 행이 고아로 남지 않는다.
   *
   * @param removedKeys 재사용으로 밀려난 옛 저장소 객체 키를 여기에 담는다(커밋 후 정리 대상).
   */
  private Image resolveProfileImage(
      Image current, StoredObject stored, boolean removeImage, List<String> removedKeys) {
    if (stored != null) {
      if (current != null) {
        if (current.isStoredByUs()) {
          removedKeys.add(current.getObjectKey()); // 덮어쓰기 전에 옛 키를 정리 대상에 올린다
        }
        current.updateMinio(stored.url(), stored.objectKey());
        return current;
      }
      return imageRepository.save(Image.ofMinio(stored.url(), stored.objectKey()));
    }
    if (removeImage) {
      return null; // 제거 — 호출자가 옛 행과 실물을 정리한다
    }
    return current; // 이미지는 건드리지 않는 수정
  }

  private boolean hasContent(MultipartFile image) {
    return image != null && !image.isEmpty();
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  // 커밋/롤백에 따라 DB 와 저장소를 일치시키는 훅. 커밋되면 밀려난 옛 객체를, 롤백되면 새로 올린 객체를 지운다.
  private void registerImageCleanup(String storedKey, List<String> removedKeys) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            List<String> targets =
                (status == STATUS_COMMITTED)
                    ? removedKeys
                    : (storedKey != null ? List.of(storedKey) : List.of());
            for (String key : targets) {
              try {
                fileStorageService.deleteByKey(key);
              } catch (RuntimeException e) {
                // 정리 실패로 요청 자체를 실패시키지는 않는다. 객체가 남는 것보다 나쁜 게 없으므로 로그만 남긴다.
                log.warn("프로필 이미지 정리 실패. objectKey={}", key, e);
              }
            }
          }
        });
  }
}
