package com.basecamp.backend.domain.camp.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.storage.FileStorageService;
import com.basecamp.backend.common.storage.ImageCategory;
import com.basecamp.backend.common.storage.StoredObject;
import com.basecamp.backend.domain.camp.client.kakao.GeoPoint;
import com.basecamp.backend.domain.camp.client.kakao.KakaoGeocodingClient;
import com.basecamp.backend.domain.camp.dto.request.CampRegistrationRequest;
import com.basecamp.backend.domain.camp.dto.request.CampUpdateRequest;
import com.basecamp.backend.domain.camp.dto.request.GocampingApiResponseDto;
import com.basecamp.backend.domain.camp.dto.response.CampResponseDto;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.entity.CampManageStatus;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.camp.repository.CampSpecs;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j // 실제 로깅 도구를 감싸는 창구/인터페이스, 이 클래스에서 로그 찍을 수 있는 로그는 변수를 자동으로 만들어 주는 애너테이션
@Service
@RequiredArgsConstructor
public class CampService {

  // campRepository 를 자동으로 주입 받기
  private final CampRepository campRepository;
  private final RestTemplate restTemplate;

  // 사용자가 입력한 주소(addr1)를 좌표(mapX/mapY)로 바꿔주는 지오코딩 클라이언트.
  // registerCamp()/updateCamp() 에서 사용한다.
  private final KakaoGeocodingClient kakaoGeocodingClient;
  // 캠핑장 이미지를 저장소(MinIO)에 올리고 공개 URL·객체 키를 돌려주는 저장 서비스
  private final FileStorageService fileStorageService;
  // 외부 호출(지오코딩·업로드)을 끝낸 뒤 DB 반영만 짧은 트랜잭션으로 처리하는 협력 빈
  private final CampTransactionService campTransactionService;

  @Value("${gocamping.api.key}")
  private String gocampingApiKey;

  @Value("${gocamping.api.url}")
  private String gocampingApiUrl;

  // 고캠핑 API가 가격 정보를 제공하지 않아, 신규 캠핑장 저장 시 이 범위 내에서 임의로 가격을 부여한다.
  @Value("${camp.default-price.min}")
  private int defaultPriceMin;

  @Value("${camp.default-price.max}")
  private int defaultPriceMax;

  @Value("${camp.default-price.unit}")
  private int defaultPriceUnit;

  // camp.default-price.* 설정이 잘못되면(min > max, unit <= 0) generateRandomPrice()가 나중에
  // ArithmeticException/IllegalArgumentException으로 조용히 실패하므로, 앱 시작 시점에 미리 검증한다.
  @PostConstruct
  private void validatePricePolicy() {
    if (defaultPriceMin < 0 || defaultPriceMax < defaultPriceMin || defaultPriceUnit <= 0) {
      throw new IllegalStateException(
          String.format(
              "camp.default-price 설정이 올바르지 않습니다. (min=%d, max=%d, unit=%d) "
                  + "min >= 0, max >= min, unit > 0 이어야 합니다.",
              defaultPriceMin, defaultPriceMax, defaultPriceUnit));
    }
  }

  // 고캠핑 API 에서 받은 캠핑장 데이터 DB 저장
  // 반환값은 "실제로 새로 저장한 건수". 받은 개수(apiCamps.size())와 다르다 —
  // 이미 있는 contentId 와 대표 이미지가 없는 항목은 걸러지기 때문.
  @Transactional
  public int saveCampsFromApi(List<GocampingApiResponseDto> apiCamps) {
    // API 에서 받은 데이터가 없다면 ? 메서드 종료
    if (apiCamps == null || apiCamps.isEmpty()) {
      return 0;
    }
    // DB 에 이미 저장이 된 contentId를 모두 가져오기
    Set<Long> existingContentIds =
        campRepository.findAllContentIds().stream().collect(Collectors.toSet());

    // 새로운 데이터만 필터링 하고 Entity로 변환 하기
    List<Camp> newCamps =
        apiCamps.stream()
            .filter(dto -> !existingContentIds.contains(dto.getContentId()))
            .filter(dto -> StringUtils.hasText(dto.getFirstImageUrl()))
            .map(dto -> Camp.fromGocampingApi(dto, generateRandomPrice()))
            .collect(Collectors.toList());
    // 새로운 데이터 DB 저장 로직
    if (!newCamps.isEmpty()) {
      campRepository.saveAll(newCamps);
    }
    return newCamps.size();
  }

  // 고캠핑 API 가격 설정
  // 없는 가격 정보를 대체하기 위해 설정된 범위 내에서 unit 단위로 임의 가격을 생성한다.
  private int generateRandomPrice() {
    int steps = (defaultPriceMax - defaultPriceMin) / defaultPriceUnit + 1;
    return defaultPriceMin + ThreadLocalRandom.current().nextInt(steps) * defaultPriceUnit;
  }

  /**
   * 고캠핑 공공데이터 API에서 캠핑장 데이터를 받아와서 DB에 저장
   *
   * <p>순서: 1. 고캠핑 API에 요청 (RestTemplate 사용) 2. 응답 받음 (JSON) 3. saveCampsFromApi()를 호출해서 DB에 저장
   */
  @Transactional
  public void fetchAndSaveCampsFromGocampingApi() {
    try {
      int pageNo = 1;
      int numOfRows = 100;
      boolean hasMoreData = true;
      int totalReceived = 0;
      int totalSaved = 0;

      while (hasMoreData) {
        String url =
            gocampingApiUrl
                + "?serviceKey="
                + gocampingApiKey
                + "&numOfRows="
                + numOfRows
                + "&pageNo="
                + pageNo
                + "&MobileOS=ETC&MobileApp=basecamp&_type=json";

        log.info("고캠핑 API 호출 중... (페이지: {})", pageNo);

        ResponseEntity<GocampingApiResponse> response =
            restTemplate.getForEntity(url, GocampingApiResponse.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
          ResponseBody responseBody = response.getBody().getResponse();
          Body body = responseBody != null ? responseBody.getBody() : null;
          Items items = body != null ? body.getItems() : null;
          List<GocampingApiResponseDto> camps = items != null ? items.getItem() : null;

          log.debug(
              "고캠핑 API 응답 - 페이지: {}, 받은 캠핑장 개수: {}", pageNo, camps != null ? camps.size() : 0);
          if (camps != null && !camps.isEmpty()) {
            GocampingApiResponseDto firstCamp = camps.get(0);
            log.debug(
                "첫 번째 캠핑장 - contentId: {}, facltNm: {}, addr1: {}",
                firstCamp.getContentId(),
                firstCamp.getFacltNm(),
                firstCamp.getAddr1());
          }

          if (camps == null || camps.isEmpty()) {
            log.info("모든 페이지를 받았습니다");
            hasMoreData = false;
          } else {
            // receivedCount(API 응답 개수)와 savedCount(실제 저장 건수)는 다르다.
            // 페이지네이션 종료 판단은 반드시 receivedCount 로 해야 한다 —
            // savedCount 로 하면 전부 중복인 페이지에서 0건이 나와 조기 종료된다.
            int receivedCount = camps.size();
            int savedCount = saveCampsFromApi(camps);

            totalReceived += receivedCount;
            totalSaved += savedCount;
            log.info("페이지 {}: {}건 수신, {}건 신규 저장", pageNo, receivedCount, savedCount);

            if (receivedCount < numOfRows) {
              log.info("마지막 페이지입니다");
              hasMoreData = false;
            }

            pageNo++;
          }
        } else {
          throw new BusinessException(ErrorCode.GOCAMPING_SERVER_ERROR, "고캠핑 API 응답이 비정상입니다");
        }
      }

      log.info(
          "총 {}건 수신, {}개의 캠핑장이 새로 저장되었습니다 (중복/대표이미지 없음 {}건 제외)",
          totalReceived,
          totalSaved,
          totalReceived - totalSaved);

    } catch (BusinessException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("고캠핑 API 호출 실패: {}", e.getClass().getSimpleName());
      throw new BusinessException(ErrorCode.GOCAMPING_SERVER_ERROR, "고캠핑 API 호출 중 오류가 발생했습니다");
    }
  }

  // 특정 캠핑장 ID 조회 (Read). 없으면 CAMP_NOT_FOUND.
  @Transactional(readOnly = true)
  public Camp getCampId(Long campId) {
    return campRepository
        .findById(campId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND, "캠핑장을 찾을 수 없습니다"));
  }

  // 고캠핑 API의 contentId로 캠핑장 조회 (Read). 없으면 CAMP_NOT_FOUND.
  @Transactional(readOnly = true)
  public Camp getCampByContentId(Long contentId) {
    return campRepository
        .findByContentId(contentId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND, "캠핑장을 찾을 수 없습니다"));
  }

  // 상세 조회 전용. 갤러리를 함께 내려주므로 이미지까지 초기화해서 반환한다.
  // 컨트롤러에서 DTO 로 바꾸는 시점에는 open-in-view: false 라 트랜잭션이 닫혀 있어, 여기서 미리 로딩해야 한다.
  @Transactional(readOnly = true)
  public Camp getCampDetail(Long campId) {
    return campRepository
        .findWithImagesByCampId(campId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND, "캠핑장을 찾을 수 없습니다"));
  }

  @Transactional(readOnly = true)
  public Camp getCampDetailByContentId(Long contentId) {
    return campRepository
        .findWithImagesByContentId(contentId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND, "캠핑장을 찾을 수 없습니다"));
  }

  // 모든 캠핑장 조회
  @Transactional(readOnly = true)
  public List<Camp> getAllCamps() {
    return campRepository.findAll();
  }

  // 캠핑장 이름으로 검색
  @Transactional(readOnly = true)
  public List<Camp> searchByName(String name) {
    return campRepository.findByFacltNmContaining(name);
  }

  // 특정 지역 캠핑장 검색
  @Transactional(readOnly = true)
  public List<Camp> searchByAddress(String address) {
    return campRepository.findByAddr1Containing(address);
  }

  // 키워드/지역/유형/최대금액 필터 + 정렬 + 페이징을 조합한 캠핑장 목록/상세검색 조회
  // sort: recommended(기본, 평점->예약건수순) / rating(평점순) / reviewCount(리뷰많은순) / priceAsc(가격낮은순) /
  // recent(최근등록순)
  @Transactional(readOnly = true)
  public Page<CampResponseDto> searchCamps(
      String keyword,
      String region,
      String induty,
      Integer priceMax,
      String sort,
      int pageNo,
      int numOfRows) {
    if (numOfRows <= 0) {
      throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE, "numOfRows는 1이상이어야 합니다");
    }
    int page = Math.max(pageNo - 1, 0);
    Page<Camp> result;

    if ("reviewCount".equals(sort)) {
      Pageable pageable = PageRequest.of(page, numOfRows);
      result =
          campRepository.searchOrderByReviewCountDesc(keyword, region, induty, priceMax, pageable);
    } else {
      Specification<Camp> spec =
          Specification.where(CampSpecs.isOperating())
              .and(CampSpecs.keywordContains(keyword))
              .and(CampSpecs.regionContains(region))
              .and(CampSpecs.indutyContains(induty))
              .and(CampSpecs.priceLessThanOrEqual(priceMax));
      Pageable pageable = PageRequest.of(page, numOfRows, resolveSort(sort));
      result = campRepository.findAll(spec, pageable);
    }

    // Page<Camp> → Page<CampResponseDto> (봉투는 전역 래퍼에 맡김, 프론트는 data.content/data.totalElements 사용)
    return result.map(CampResponseDto::from);
  }

  // HOT 캠핑장 조회 (평점순 / 예약건수순)
  @Transactional(readOnly = true)
  // 예약건수순: reservations 실시간 COUNT (PENDING+RESERVED만, 결제 완료된 예약만 유효하게 카운트)
  // 평점순: 캐싱된 average_rating 컬럼 기준, 동점이면 예약건수로 2차 정렬
  public Page<CampResponseDto> getHotCamps(String sortBy, int pageNo, int numOfRows) {
    if (numOfRows <= 0) {
      throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE, "numOfRows는 1이상이어야 합니다");
    }
    int page = Math.max(pageNo - 1, 0);
    Page<Camp> result;
    if ("reservationCount".equals(sortBy)) {
      List<String> statuses = List.of("PENDING", "RESERVED");
      Pageable pageable = PageRequest.of(page, numOfRows);
      result = campRepository.findHotCampsByReservationCountDesc(statuses, pageable);
    } else {
      Sort sort =
          Sort.by(Sort.Direction.DESC, "averageRating")
              .and(Sort.by(Sort.Direction.DESC, "reservationCount"))
              .and(Sort.by(Sort.Direction.ASC, "campId"));
      Pageable pageable = PageRequest.of(page, numOfRows, sort);
      result = campRepository.findAll(CampSpecs.isOperating(), pageable);
    }
    // Page<Camp> → Page<CampResponseDto> (봉투는 전역 래퍼에 맡김, 프론트는 data.content/data.totalElements 사용)
    return result.map(CampResponseDto::from);
  }

  private Sort resolveSort(String sort) {
    if ("rating".equals(sort)) return Sort.by(Sort.Direction.DESC, "averageRating");
    if ("priceAsc".equals(sort)) return Sort.by(Sort.Direction.ASC, "price");
    if ("recent".equals(sort)) return Sort.by(Sort.Direction.DESC, "createdAt");
    // 추천순(recommended, 기본값): 평점 우선, 동점이면 예약건수 많은 순
    return Sort.by(Sort.Direction.DESC, "averageRating")
        .and(Sort.by(Sort.Direction.DESC, "reservationCount"));
  }

  // 고캠핑 API 실제 응답 구조: { "response": { "header": {...}, "body": { "items": { "item": [...] }, ... }
  // } }
  // 여기 선언되지 않은 필드(header, numOfRows, pageNo, totalCount 등)는 무시한다
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class GocampingApiResponse {
    private ResponseBody response;

    public ResponseBody getResponse() {
      return response;
    }

    public void setResponse(ResponseBody response) {
      this.response = response;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class ResponseBody {
    private Body body;

    public Body getBody() {
      return body;
    }

    public void setBody(Body body) {
      this.body = body;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class Body {
    private Items items;

    public Items getItems() {
      return items;
    }

    public void setItems(Items items) {
      this.items = items;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class Items {
    private List<GocampingApiResponseDto> item;

    public List<GocampingApiResponseDto> getItem() {
      return item;
    }

    public void setItem(List<GocampingApiResponseDto> item) {
      this.item = item;
    }
  }

  /*
   * 고객이 직접 캠핑장을 등록/수정/삭제
   */

  // 캠핑장 등록 비지니스 로직
  // 지오코딩(외부 HTTP)과 이미지 업로드(저장소 IO)를 트랜잭션 밖에서 먼저 끝내고,
  // DB 반영만 CampTransactionService 의 짧은 쓰기 트랜잭션에 맡긴다.
  // 이 메서드 자체에 @Transactional을 걸지 않는 이유 — 외부 호출 지연이 DB 커넥션을 점유하지 않도록.
  public Camp registerCamp(
      CampRegistrationRequest request, Long ownerId, List<MultipartFile> images) {
    // 권한 검증 : ownerId가 없다면 등록이 불가하도록 설정
    // ownerId == null : 인증 정보 자체가 없는 것 ( 로그인을 안함, 토큰이 없음 )
    // ownerId <= 0 : 이상한 값 ( 있을 수 없는 ID )
    if (ownerId == null || ownerId <= 0) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED, "캠핑장 등록 권한이 없습니다.");
    }

    // 주소로 좌표(mapX/mapY)를 조회한다. 못 찾아도 등록은 그대로 진행한다(좌표만 null).
    GeoPoint geoPoint = kakaoGeocodingClient.geocode(request.getAddr1());

    // DTO -> Entity 변환 하는 코드
    Camp camp =
        Camp.builder()
            .facltNm(request.getFacltNm())
            .addr1(request.getAddr1())
            .addr2(request.getAddr2())
            .mapX(geoPoint != null ? geoPoint.mapX() : null)
            .mapY(geoPoint != null ? geoPoint.mapY() : null)
            .tel(request.getTel())
            .induty(request.getInduty())
            .price(request.getPrice())
            .gnrlSiteCo(request.getGnrlSiteCo() != null ? request.getGnrlSiteCo() : 0)
            .autoSiteCo(request.getAutoSiteCo() != null ? request.getAutoSiteCo() : 0)
            .glampSiteCo(request.getGlampSiteCo() != null ? request.getGlampSiteCo() : 0)
            .lineIntro(request.getLineIntro())
            .firstImageUrl(request.getFirstImageUrl())
            .homepage(request.getHomepage())
            .ownerId(ownerId)
            .contentId(null)
            .manageSttus(CampManageStatus.OPERATING)
            .averageRating(new BigDecimal("0.00"))
            .reservationCount(0)
            .createdAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")))
            .build();

    // 이미지를 저장소에 올린다. 형식·개수 검증과 부분 실패 되돌림은 storeAll이 담당한다.
    List<StoredObject> storedImages = fileStorageService.storeAll(images, ImageCategory.CAMP);

    try {
      return campTransactionService.register(camp, storedImages);
    } catch (RuntimeException e) {
      // DB 저장이 실패하면 방금 올린 객체가 저장소에 고아로 남는다. 되돌린다.
      deleteQuietly(storedImages.stream().map(StoredObject::objectKey).toList());
      throw e;
    }
  }

  // 로그인한 회원(ownerId)이 등록한 캠핑장 목록 조회
  @Transactional(readOnly = true)
  public List<CampResponseDto> getMyCamps(Long ownerId) {
    if (ownerId == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
      // "너 누군지 모르겠다" → 인증 자체가 없는 상황 → 401
    }

    if (ownerId <= 0) {
      throw new BusinessException(ErrorCode.CAMP_NOT_ACCESSED, "캠핑장 등록 권한이 없습니다.");
      // "값은 있는데 이상하다(음수/0)" → 뭔가 조작되었거나 잘못된 값 → 403
    }

    List<Camp> camps = campRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    // 비페이징 목록 — camp 도메인의 getAllCamps/searchByName 과 동일하게 순수 List 반환
    return camps.stream().map(CampResponseDto::from).toList();
  }

  // 캠핑장 정보 수정 ( 본인이 등록한 캠핑장만 가능하도록 )
  // 지오코딩과 이미지 업로드를 DB 트랜잭션 밖에서 먼저 끝낸 뒤, 짧은 쓰기 트랜잭션에서 반영한다.
  public Camp updateCamp(
      Long campId, CampUpdateRequest request, Long ownerId, List<MultipartFile> images) {

    // 주소가 바뀌면 좌표도 다시 조회한다. DB 트랜잭션을 시작하기 전에 호출해서
    // 카카오 API 지연이 DB 커넥션을 점유하지 않도록 한다.
    GeoPoint geoPoint =
        request.getAddr1() != null ? kakaoGeocodingClient.geocode(request.getAddr1()) : null;

    // 새 이미지를 저장소에 올린다. 소유권 검증보다 앞서지만, 남의 캠핑장이면 아래에서 예외가 나고
    // catch 에서 방금 올린 객체를 지우므로 고아로 남지 않는다.
    List<StoredObject> storedImages = fileStorageService.storeAll(images, ImageCategory.CAMP);

    CampTransactionService.UpdateResult result;
    try {
      result = campTransactionService.update(campId, request, geoPoint, storedImages, ownerId);
    } catch (RuntimeException e) {
      deleteQuietly(storedImages.stream().map(StoredObject::objectKey).toList());
      throw e;
    }

    // 커밋이 끝난 뒤에야 옛 객체를 지운다. 커밋 전에 지우면 롤백 시 DB 에 살아 있는 이미지의
    // 실물이 사라져 깨진 링크가 된다.
    deleteQuietly(result.removedObjectKeys());
    return result.camp();
  }

  // 캠핑장 삭제 기능 구현 (소프트 삭제). 첨부 이미지는 완전히 지운다.
  public void deleteCamp(Long campId, Long ownerId) {
    List<String> removedObjectKeys = campTransactionService.softDelete(campId, ownerId);
    deleteQuietly(removedObjectKeys);
  }

  // 저장소 정리는 실패해도 요청 자체를 실패시키지 않는다. 객체가 남는 것보다 나쁜 게 없으므로 로그만 남긴다.
  private void deleteQuietly(List<String> objectKeys) {
    for (String key : objectKeys) {
      try {
        fileStorageService.deleteByKey(key);
      } catch (RuntimeException e) {
        log.warn("캠핑장 이미지 정리 실패. objectKey={}", key, e);
      }
    }
  }
}
