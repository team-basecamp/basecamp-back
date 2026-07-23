package com.basecamp.backend.domain.camp.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.storage.StoredObject;
import com.basecamp.backend.domain.camp.client.kakao.GeoPoint;
import com.basecamp.backend.domain.camp.dto.request.CampUpdateRequest;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.reservation.service.ReservationService;
import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.repository.ImageRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠핑장 쓰기 작업 중 <b>DB 트랜잭션이 필요한 부분만</b> 담당한다.
 *
 * <p>{@link CampService} 의 등록·수정은 지오코딩(카카오 HTTP)과 이미지 업로드(저장소 IO)를 동반한다. 이들을 트랜잭션 안에서 하면 외부 호출이
 * 지연되는 동안 DB 커넥션을 붙잡게 되고, 롤백해도 이미 올라간 객체는 되돌아오지 않는다. 그래서 외부 호출은 {@code CampService} 가 트랜잭션 밖에서 먼저
 * 끝내고, 그 결과만 이 빈에 넘겨 짧은 쓰기 트랜잭션으로 반영한다. ({@code AuthTransactionService} 와 같은 구조)
 *
 * <p>이미지 교체·삭제 메서드는 <b>정리해야 할 저장소 객체 키</b>를 돌려준다. 저장소는 트랜잭션에 참여하지 않으므로, 커밋이 끝난 뒤 호출자가 그 키로 실물을 지운다.
 * 커밋 전에 지우면 롤백 시 DB 에 살아 있는 이미지의 실물이 사라져 깨진 링크가 된다.
 */
@Service
@RequiredArgsConstructor
public class CampTransactionService {

  private final CampRepository campRepository;
  private final ImageRepository imageRepository;
  private final ReservationService reservationService;

  /** 새 캠핑장을 이미지와 함께 저장한다. */
  @Transactional
  public Camp register(Camp camp, List<StoredObject> storedImages) {
    camp.attachImages(toImages(storedImages));
    // cascade=PERSIST 로 images 행과 camp_images 연결이 함께 저장된다.
    return campRepository.save(camp);
  }

  /**
   * 캠핑장 정보와 이미지를 반영하고, 이번 수정으로 떨어져 나간 저장소 객체 키를 돌려준다.
   *
   * @param geoPoint 주소가 바뀌었을 때만 의미가 있다. 주소를 안 바꿨으면 호출자가 {@code null} 을 넘기고 좌표는 손대지 않는다.
   */
  @Transactional
  public UpdateResult update(
      Long campId,
      CampUpdateRequest request,
      GeoPoint geoPoint,
      List<StoredObject> storedImages,
      Long ownerId) {

    Camp camp = loadOwned(campId, ownerId);

    camp.updateInfo(request);

    // 지오코딩 실패 시 좌표는 비운다 (새 주소와 옛 좌표가 어긋난 채로 남지 않도록)
    if (request.getAddr1() != null) {
      camp.updateLocation(geoPoint);
    }

    // 남길 기존 이미지 + 새로 올린 이미지 순서로 최종 목록을 만든다. 이 순서가 곧 노출 순서다.
    List<Image> finalImages = new ArrayList<>(resolveKeptImages(camp, request.getKeepImageUrls()));
    finalImages.addAll(toImages(storedImages));

    List<Image> detached = camp.replaceImages(finalImages);

    // 연결(camp_images)만 끊고 두면 images 행이 고아로 쌓이므로 행까지 지운다.
    // 삭제 순서는 Hibernate 가 보장한다 — 같은 flush 안에서 컬렉션 삭제가 엔티티 삭제보다 먼저 나간다.
    if (!detached.isEmpty()) {
      imageRepository.deleteAll(detached);
    }

    return new UpdateResult(campRepository.save(camp), objectKeysOf(detached));
  }

  /** 캠핑장을 소프트 삭제하고, 함께 정리해야 할 저장소 객체 키를 돌려준다. */
  @Transactional
  public List<String> softDelete(Long campId, Long ownerId) {
    Camp camp = loadOwned(campId, ownerId);

    // 진행 중인 예약을 먼저 취소·환불 처리한다.
    reservationService.cancelAllForDeletedCamp(campId);

    // 캠핑장 자체는 소프트 삭제지만 이미지는 하드 삭제다. 게시글 삭제와 같은 기준 —
    // 남겨두면 저장소만 계속 불어나고, URL 을 아는 사람은 지운 사진을 계속 열어볼 수 있다.
    List<Image> detached = camp.replaceImages(List.of());
    if (!detached.isEmpty()) {
      imageRepository.deleteAll(detached);
    }

    camp.softDelete();
    return objectKeysOf(detached);
  }

  /** 이미지까지 로딩해 소유권을 확인한다. 교체 대상을 인스턴스로 되짚어야 하므로 컬렉션이 초기화돼 있어야 한다. */
  private Camp loadOwned(Long campId, Long ownerId) {
    Camp camp =
        campRepository
            .findWithImagesByCampId(campId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND, "캠핑장을 찾을 수 없습니다"));

    if (!Objects.equals(camp.getOwnerId(), ownerId)) {
      throw new BusinessException(ErrorCode.CAMP_NOT_ACCESSED, "본인이 등록한 캠핑장이 아닙니다");
    }
    return camp;
  }

  /**
   * 요청의 keepImageUrls 를 이 캠핑장에 실제로 붙어 있는 Image 인스턴스로 되짚는다.
   *
   * <p>URL 문자열을 믿고 새 Image 를 만들면 안 된다. 남의 캠핑장 이미지 URL 을 실어 내 캠핑장에 붙이거나 임의 URL 을 DB 에 심을 수 있기 때문이다.
   * 그래서 "지금 이 캠핑장에 붙어 있는 것"만 통과시키고 나머지는 400 으로 막는다. 중복도 막는다 — 같은 Image 가 두 번 들어가면 camp_images 키가
   * 깨진다.
   */
  private List<Image> resolveKeptImages(Camp camp, List<String> keepImageUrls) {
    if (keepImageUrls == null) {
      return List.copyOf(camp.getImages()); // 이미지는 건드리지 않는 수정
    }
    if (keepImageUrls.isEmpty()) {
      return List.of(); // 전부 삭제
    }

    Map<String, Image> currentByUrl =
        camp.getImages().stream().collect(Collectors.toMap(Image::getImageUrl, image -> image));

    List<Image> kept = new ArrayList<>(keepImageUrls.size());
    for (String url : keepImageUrls) {
      Image image = currentByUrl.get(url);
      if (image == null || kept.contains(image)) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
      }
      kept.add(image);
    }
    return kept;
  }

  private List<Image> toImages(List<StoredObject> storedImages) {
    if (storedImages == null) {
      return List.of();
    }
    return storedImages.stream().map(o -> Image.ofMinio(o.url(), o.objectKey())).toList();
  }

  // 외부 URL 이미지는 키가 없어 걸러진다 — 우리 소유가 아니라 지울 수 없다.
  private List<String> objectKeysOf(List<Image> images) {
    return images.stream().filter(Image::isStoredByUs).map(Image::getObjectKey).toList();
  }

  /** 수정 결과와, 커밋 후 정리해야 할 저장소 객체 키. */
  public record UpdateResult(Camp camp, List<String> removedObjectKeys) {}
}
