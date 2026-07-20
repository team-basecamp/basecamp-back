package com.basecamp.backend.domain.review.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.storage.FileStorageService;
import com.basecamp.backend.common.storage.ImageCategory;
import com.basecamp.backend.common.storage.MinioProperties;
import com.basecamp.backend.common.storage.StoredObject;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.reservation.entity.Reservation;
import com.basecamp.backend.domain.reservation.entity.ReservationStatus;
import com.basecamp.backend.domain.reservation.repository.ReservationRepository;
import com.basecamp.backend.domain.review.dto.request.ReviewRequest;
import com.basecamp.backend.domain.review.dto.request.ReviewUpdateRequest;
import com.basecamp.backend.domain.review.dto.response.ReviewResponse;
import com.basecamp.backend.domain.review.entity.Review;
import com.basecamp.backend.domain.review.repository.ReviewRepository;
import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.repository.ImageRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

// 리뷰 비즈니스 로직. 기본은 읽기 전용 트랜잭션, 쓰기 메서드에만 @Transactional을 따로 건다.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final ReservationRepository reservationRepository;
  private final CampRepository campRepository;
  // 첨부 이미지를 저장소(MinIO)에 올리고 공개 URL을 돌려주는 저장 서비스
  private final FileStorageService fileStorageService;
  // 리뷰에서 떨어져 나간 이미지 행을 정리하기 위한 리포지토리
  private final ImageRepository imageRepository;
  // 첨부 개수 상한 등 업로드 정책 (수정은 기존+신규 합계로 상한을 봐야 한다)
  private final MinioProperties minioProperties;

  // 리뷰 작성: 예약 소유자가 체크아웃을 마친 예약에 한해 리뷰를 남길 수 있다. (쓰기 트랜잭션)
  // images는 선택 사항(null/빈 목록 가능)이며, 있으면 로컬 저장소에 올린 상대경로로 Image를 만들어 함께 저장한다.
  @Transactional
  public ReviewResponse createReview(
      Long userId, Long reservationId, ReviewRequest request, List<MultipartFile> images) {
    // 리뷰를 달 예약을 먼저 조회한다.
    Reservation reservation =
        reservationRepository
            .findById(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    // 예약 소유자(예약자) 본인만 리뷰를 작성할 수 있다.
    if (!reservation.getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    // 체크아웃이 완료된(확정 + 체크아웃 날짜 경과) 예약만 리뷰를 쓸 수 있다.
    validateCheckedOut(reservation);

    // 예약당 리뷰는 한 건(reviews.reservation_id UNIQUE). 이미 있으면 409.
    if (reviewRepository.existsByReservation_Id(reservationId)) {
      throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    // 리뷰 대상 캠핑장은 예약의 캠핑장을 그대로 따른다. (별도 입력 없이 예약에서 파생)
    Review review =
        Review.builder()
            .reservation(reservation)
            .camp(reservation.getCamp())
            .rating(request.rating())
            .content(request.content())
            .build();

    // 리뷰 이미지 추가
    List<StoredObject> storedObjects = fileStorageService.storeAll(images, ImageCategory.REVIEW);

    // 커밋 실패로 롤백되면 객체만 고아로 남으므로, 커밋이 성공하지 못한 모든 경우에 저장했던 객체를 되돌린다.
    registerImageCleanup(storedObjects.stream().map(StoredObject::objectKey).toList(), List.of());

    review.attachImages(
        storedObjects.stream().map(o -> Image.ofMinio(o.url(), o.objectKey())).toList());

    try {
      // cascade=PERSIST 로 Image 행과 review_images 연결이 함께 저장된다.
      reviewRepository.saveAndFlush(review);
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    refreshCampAverageRating(review.getCamp().getCampId());
    return ReviewResponse.from(review);
  }

  // 리뷰 수정: 리뷰를 조회해 예약 소유자 본인일 때만 평점·본문과 첨부 이미지를 갈아끼운다. (쓰기 트랜잭션)
  // 이미지는 게시글 수정과 같은 "전체 교체"다.
  @Transactional
  public ReviewResponse updateReview(
      Long userId, Long reviewId, ReviewUpdateRequest request, List<MultipartFile> images) {
    // 수정할 리뷰를 예약·작성자와 함께 조회한다. 없으면 404.
    Review review =
        reviewRepository
            .findByIdWithReservation(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

    // 예약 소유자(작성자) 본인만 수정할 수 있다. 아니면 403.
    if (!review.getReservation().getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    // 남길 기존 이미지를 확정한다.
    List<Image> keptImages = resolveKeptImages(review, request.keepImageUrls());

    // 새 이미지를 저장소에 올린다. 형식·크기 검증과 실패 시 되돌림은 storeAll이 담당한다.
    List<StoredObject> storedObjects = fileStorageService.storeAll(images, ImageCategory.REVIEW);
    List<String> storedKeys = storedObjects.stream().map(StoredObject::objectKey).toList();

    // DB 커밋 결과에 맞춰야 하므로, 새 객체 되돌림 훅을 storeAll 직후 걸어 롤백 시 고아 객체를, 커밋 시 옛 객체를 정리한다.
    List<String> removedKeys = new ArrayList<>(); // 삭제할 이미지 저장
    registerImageCleanup(storedKeys, removedKeys);

    List<Image> newImages =
        storedObjects.stream().map(o -> Image.ofMinio(o.url(), o.objectKey())).toList();

    // 최종 개수 상한은 체크
    if (keptImages.size() + newImages.size() > minioProperties.getMaxCount()) {
      throw new BusinessException(ErrorCode.IMAGE_COUNT_EXCEEDED);
    }

    // 관리 상태 엔티티라 update 후 트랜잭션 커밋 시 변경 감지로 UPDATE가 나간다.
    review.update(request.rating(), request.content());

    // 남길 기존 것 + 새로 올린 것 순서로 최종 목록을 만든다. 이 순서가 곧 노출 순서다.
    List<Image> finalImages = new ArrayList<>(keptImages);
    finalImages.addAll(newImages);
    List<Image> detachedImages = review.replaceImages(finalImages);

    if (!detachedImages.isEmpty()) {
      imageRepository.deleteAll(detachedImages);
      // 저장소 객체 삭제는 커밋 성공 후에. 위에서 건 훅이 이 목록을 보고 지운다.
      // 외부 URL 이미지는 키가 없어 걸러진다 — 남의 이미지를 지우려 시도하지 않는다.
      detachedImages.stream()
          .filter(Image::isStoredByUs)
          .map(Image::getObjectKey)
          .forEach(removedKeys::add);
    }

    refreshCampAverageRating(review.getCamp().getCampId());
    return ReviewResponse.from(review);
  }

  // 리뷰 삭제: 리뷰를 조회해 예약 소유자 본인일 때만 실제 행을 삭제한다. (하드 삭제)
  @Transactional
  public void deleteReview(Long userId, Long reviewId) {
    // 삭제할 리뷰를 예약·작성자와 함께 조회한다. 없으면 404.
    Review review =
        reviewRepository
            .findByIdWithReservation(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

    // 예약 소유자(작성자) 본인만 삭제할 수 있다. 아니면 403.
    if (!review.getReservation().getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    // 캠핑장 식별자는 delete 전에 잡아둔다. (삭제 후엔 review에서 꺼내 쓰지 않는다)
    Long campId = review.getCamp().getCampId();

    // 첨부 이미지를 전부 떼어낸다. 리뷰는 하드 삭제라 review_images 는 FK ON DELETE CASCADE 로도 정리되지만,
    List<Image> detachedImages = review.replaceImages(List.of());

    // 실물이 사라져 깨진 링크가 된다. 이 경로에서 새로 올리는 객체는 없으므로 롤백 시 지울 목록은 비어 있다.
    List<String> removedKeys = new ArrayList<>();
    registerImageCleanup(List.of(), removedKeys);

    if (!detachedImages.isEmpty()) {
      imageRepository.deleteAll(detachedImages);
      detachedImages.stream()
          .filter(Image::isStoredByUs)
          .map(Image::getObjectKey)
          .forEach(removedKeys::add);
    }

    reviewRepository.delete(review);

    refreshCampAverageRating(campId);
  }

  // 캠핑장 리뷰 목록 조회: 해당 캠핑장의 리뷰를 최신순으로 반환한다. (읽기 전용 트랜잭션)
  public List<ReviewResponse> getReviewsByCamp(Long campId) {
    return reviewRepository.findByCampIdWithUser(campId).stream()
        .map(ReviewResponse::from)
        .toList();
  }

  // 내가 쓴 리뷰 목록 조회: 로그인한 회원이 작성한 리뷰를 최신순으로 반환한다. (읽기 전용 트랜잭션)
  public List<ReviewResponse> getMyReviews(Long userId) {
    return reviewRepository.findByReservationUserIdWithDetails(userId).stream()
        .map(ReviewResponse::from)
        .toList();
  }

  // camps.average_rating 재계산: 리뷰가 바뀔 때마다 해당 캠핑장의 평균을 다시 집계해 캐싱 컬럼에 반영한다.
  // 집계·반영을 UPDATE 한 문장으로 처리해 같은 캠핑장에 대한 동시 갱신을 직렬화한다. (CampRepository 주석 참고)
  // 방금의 수정(변경 감지)·삭제는 아직 DB에 반영되지 않은 상태라, flushAutomatically로 UPDATE 전에 밀어넣는다.
  // campId는 프록시를 초기화하지 않고 읽히므로 삭제된 캠핑장의 리뷰를 건드려도 프록시 초기화로 터지지 않는다.
  private void refreshCampAverageRating(Long campId) {
    campRepository.refreshAverageRating(campId);
  }

  // 체크아웃 완료 검증: 예약이 확정(RESERVED) 상태이고 체크아웃 날짜가 지났을 때만 리뷰 작성을 허용한다.
  private void validateCheckedOut(Reservation reservation) {
    boolean checkedOut =
        reservation.getStatus() == ReservationStatus.RESERVED
            && reservation.getCheckOutDate().isBefore(LocalDate.now());
    if (!checkedOut) {
      throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED_BEFORE_CHECKOUT);
    }
  }

  // 이미지 중복 제거 판단
  private List<Image> resolveKeptImages(Review review, List<String> keepImageUrls) {
    // 변경 사항이 없을 경우
    if (keepImageUrls == null) {
      return List.copyOf(review.getImages());
    }
    // 있던 이미지를 다 내릴 경우
    if (keepImageUrls.isEmpty()) {
      return List.of();
    }

    Map<String, Image> currentByUrl =
        review.getImages().stream().collect(Collectors.toMap(Image::getImageUrl, image -> image));

    // 중복 검사
    List<Image> kept = new ArrayList<>(keepImageUrls.size());
    for (String url : keepImageUrls) {
      Image image = currentByUrl.get(url);
      // 이 리뷰의 이미지가 아니거나(위조·오타), 같은 것을 두 번 보냈으면 400.
      if (image == null || kept.contains(image)) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
      }
      kept.add(image);
    }
    return kept;
  }

  // 커밋과 롤백에 따라서 DB와 저장소를 일치시키는 훅.
  private void registerImageCleanup(List<String> storedKeys, List<String> removedKeys) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            List<String> targets = (status == STATUS_COMMITTED) ? removedKeys : storedKeys;
            for (String key : targets) {
              try {
                fileStorageService.deleteByKey(key);
              } catch (RuntimeException e) {
                // 정리 실패로 요청 자체를 실패시키지는 않는다. 객체가 남는 것보다 나쁜 게 없으므로 로그만 남긴다.
                log.warn("리뷰 이미지 정리 실패. objectKey={}", key, e);
              }
            }
          }
        });
  }
}
