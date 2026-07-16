package com.basecamp.backend.domain.camp.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.client.kakao.GeoPoint;
import com.basecamp.backend.domain.camp.client.kakao.KakaoGeocodingClient;
import com.basecamp.backend.domain.camp.dto.request.CampRegistrationRequest;
import com.basecamp.backend.domain.camp.dto.request.CampUpdateRequest;
import com.basecamp.backend.domain.camp.dto.request.GocampingApiResponseDto;
import com.basecamp.backend.domain.camp.dto.response.CampListResponseDto;
import com.basecamp.backend.domain.camp.dto.response.CampResponseDto;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.entity.CampManageStatus;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.camp.repository.CampSpecs;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.PostConstruct;
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
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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
            throw new IllegalStateException(String.format(
                    "camp.default-price 설정이 올바르지 않습니다. (min=%d, max=%d, unit=%d) "
                            + "min >= 0, max >= min, unit > 0 이어야 합니다.",
                    defaultPriceMin, defaultPriceMax, defaultPriceUnit));
        }
    }

    // 고캠핑 API 에서 받은 캠핑장 데이터 DB 저장
    @Transactional
    public void saveCampsFromApi(List<GocampingApiResponseDto> apiCamps){
        // API 에서 받은 데이터가 없다면 ? 메서드 종료
        if (apiCamps == null || apiCamps.isEmpty()){
            return;
        }
        // DB 에 이미 저장이 된 contentId를 모두 가져오기.
        Set<Long> existingContentIds = campRepository.findAllContentIds()
                .stream()
                .collect(Collectors.toSet());

        // 새로운 데이터만 필터링 하고 Entity로 변환 하기
        List<Camp> newCamps = apiCamps.stream()
                .filter(dto -> !existingContentIds.contains(dto.getContentId()))
                .map(dto -> Camp.fromGocampingApi(dto, generateRandomPrice()))
                .collect(Collectors.toList());
        // 새로운 데이터 DB 저장 로직
        if(!newCamps.isEmpty()){
            campRepository.saveAll(newCamps);
        }
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
     * 순서:
     * 1. 고캠핑 API에 요청 (RestTemplate 사용)
     * 2. 응답 받음 (JSON)
     * 3. saveCampsFromApi()를 호출해서 DB에 저장
     */
    @Transactional
    public void fetchAndSaveCampsFromGocampingApi() {
        try {
            int pageNo = 1;
            int numOfRows = 100;
            boolean hasMoreData = true;
            int totalSaved = 0;

            while (hasMoreData) {
                String url = gocampingApiUrl + "?serviceKey=" + gocampingApiKey
                        + "&numOfRows=" + numOfRows
                        + "&pageNo=" + pageNo
                        + "&MobileOS=ETC&MobileApp=basecamp&_type=json";

                log.info("고캠핑 API 호출 중... (페이지: {})", pageNo);

                ResponseEntity<GocampingApiResponse> response =
                        restTemplate.getForEntity(url, GocampingApiResponse.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    ResponseBody responseBody = response.getBody().getResponse();
                    Body body = responseBody != null ? responseBody.getBody() : null;
                    Items items = body != null ? body.getItems() : null;
                    List<GocampingApiResponseDto> camps = items != null ? items.getItem() : null;

                    log.debug("고캠핑 API 응답 - 페이지: {}, 받은 캠핑장 개수: {}", pageNo, camps != null ? camps.size() : 0);
                    if (camps != null && !camps.isEmpty()) {
                        GocampingApiResponseDto firstCamp = camps.get(0);
                        log.debug("첫 번째 캠핑장 - contentId: {}, facltNm: {}, addr1: {}",
                                firstCamp.getContentId(), firstCamp.getFacltNm(), firstCamp.getAddr1());
                    }

                    if (camps == null || camps.isEmpty()) {
                        log.info("모든 페이지를 받았습니다");
                        hasMoreData = false;
                    } else {
                        int savedCount = camps.size();
                        saveCampsFromApi(camps);

                        totalSaved += savedCount;
                        log.info("페이지 {}: {}개 저장됨", pageNo, savedCount);

                        if (savedCount < numOfRows) {
                            log.info("마지막 페이지입니다");
                            hasMoreData = false;
                        }

                        pageNo++;
                    }
                } else {
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "고캠핑 API 응답이 비정상입니다");
                }
            }

            log.info("총 {}개의 캠핑장이 저장되었습니다", totalSaved);

        } catch (Exception e) {
            log.error("고캠핑 API 호출 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "고캠핑 API 호출 중 오류가 발생했습니다");
        }
    }

    // 특정 캠핑장 ID 조회 (Read). 없으면 CAMP_NOT_FOUND.
    @Transactional(readOnly = true)
    public Camp getCampId(Long campId){
        return campRepository.findById(campId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND, "캠핑장을 찾을 수 없습니다"));
    }

    // 고캠핑 API의 contentId로 캠핑장 조회 (Read). 없으면 CAMP_NOT_FOUND.
    @Transactional(readOnly = true)
    public Camp getCampByContentId(Long contentId){
        return campRepository.findByContentId(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND, "캠핑장을 찾을 수 없습니다"));
    }

    // 모든 캠핑장 조회
    @Transactional(readOnly = true)
    public List <Camp> getAllCamps(){
        return campRepository.findAll();
    }

   // 캠핑장 이름으로 검색
   @Transactional(readOnly = true)
   public List<Camp> searchByName(String name) {
       return campRepository.findByFacltNmContaining(name);
   }

   //특정 지역 캠핑장 검색
   @Transactional(readOnly = true)
   public List<Camp> searchByAddress(String address) {
       return campRepository.findByAddr1Containing(address);
   }

    // 키워드/지역/유형/최대금액 필터 + 정렬 + 페이징을 조합한 캠핑장 목록/상세검색 조회
    // sort: recommended(기본, 평점->예약건수순) / rating(평점순) / reviewCount(리뷰많은순) / priceAsc(가격낮은순) / recent(최근등록순)
    @Transactional(readOnly = true)
    public CampListResponseDto searchCamps(String keyword, String region, String induty,
                                            Integer priceMax, String sort, int pageNo, int numOfRows) {
        int page = Math.max(pageNo - 1, 0);
        Page<Camp> result;

        if ("reviewCount".equals(sort)) {
            Pageable pageable = PageRequest.of(page, numOfRows);
            result = campRepository.searchOrderByReviewCountDesc(keyword, region, induty, priceMax, pageable);
        } else {
            Specification<Camp> spec = Specification.where(CampSpecs.isOperating())
                    .and(CampSpecs.keywordContains(keyword))
                    .and(CampSpecs.regionContains(region))
                    .and(CampSpecs.indutyContains(induty))
                    .and(CampSpecs.priceLessThanOrEqual(priceMax));
            Pageable pageable = PageRequest.of(page, numOfRows, resolveSort(sort));
            result = campRepository.findAll(spec, pageable);
        }

        List<CampResponseDto> dtos = result.getContent().stream().map(CampResponseDto::from).toList();
        return CampListResponseDto.ok(dtos, result.getTotalElements());
    }

    // HOT 캠핑장 조회 (평점순 / 예약건수순)
    @Transactional(readOnly = true)
    public CampListResponseDto getHotCamps(String sortBy, int pageNo, int numOfRows) {
        Sort sort = "reservationCount".equals(sortBy)
                ? Sort.by(Sort.Direction.DESC, "reservationCount")
                : Sort.by(Sort.Direction.DESC, "averageRating");
        Pageable pageable = PageRequest.of(Math.max(pageNo - 1, 0), numOfRows, sort);
        Page<Camp> result = campRepository.findAll(CampSpecs.isOperating(), pageable);
        List<CampResponseDto> dtos = result.getContent().stream().map(CampResponseDto::from).toList();
        return CampListResponseDto.ok(dtos, result.getTotalElements());
    }

    // 최근 등록된 캠핑장 조회
    @Transactional(readOnly = true)
    public CampListResponseDto getRecentCamps(int numOfRows) {
        return searchCamps(null, null, null, null, "recent", 1, numOfRows);
    }

    private Sort resolveSort(String sort) {
        if ("rating".equals(sort)) return Sort.by(Sort.Direction.DESC, "averageRating");
        if ("priceAsc".equals(sort)) return Sort.by(Sort.Direction.ASC, "price");
        if ("recent".equals(sort)) return Sort.by(Sort.Direction.DESC, "createdAt");
        // 추천순(recommended, 기본값): 평점 우선, 동점이면 예약건수 많은 순
        return Sort.by(Sort.Direction.DESC, "averageRating").and(Sort.by(Sort.Direction.DESC, "reservationCount"));
    }

    // 고캠핑 API 실제 응답 구조: { "response": { "header": {...}, "body": { "items": { "item": [...] }, ... } } }
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
    // 지오코딩(외부 HTTP 호출)이 끝난 뒤 campRepository.save()가 자체 트랜잭션으로 저장하므로,
    // 이 메서드 자체는 @Transactional을 걸지 않는다 — 카카오 API 지연이 DB 커넥션을 점유하지 않도록.
    public Camp registerCamp(CampRegistrationRequest request,Long ownerId){
        // 권한 검증 : ownerId가 없다면 등록이 불가하도록 설정
        // ownerId == null : 인증 정보 자체가 없는 것 ( 로그인을 안함, 토큰이 없음 )
        // ownerId <= 0 : 이상한 값 ( 있을 수 없는 ID )
        if(ownerId == null || ownerId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED,"캠핑장 등록 권한이 없습니다.");
        }

        // 주소로 좌표(mapX/mapY)를 조회한다. 못 찾아도 등록은 그대로 진행한다(좌표만 null).
        GeoPoint geoPoint = kakaoGeocodingClient.geocode(request.getAddr1());

        // DTO -> Entity 변환 하는 코드
        Camp camp = Camp.builder()
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

        // DB에  camping 장 저장
        return campRepository.save(camp);

    }

    // 로그인한 회원(ownerId)이 등록한 캠핑장 목록 조회
    @Transactional(readOnly = true)
    public CampListResponseDto getMyCamps(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        List<Camp> camps = campRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        List<CampResponseDto> dtos = camps.stream()
                .map(CampResponseDto::from)
                .toList();

        return CampListResponseDto.ok(dtos, dtos.size());
    }

    // 캠핑장 정보 수정 ( 본인이 등록한 캠핑장만 가능하도록 )
    // 지오코딩(외부 HTTP 호출)을 DB 트랜잭션 밖에서 먼저 끝낸 뒤, 짧은 쓰기 트랜잭션에서 저장한다.
    // 더티 체킹에 기대는 대신 명시적으로 save()를 호출한다.
    public Camp updateCamp(Long campId, CampUpdateRequest request, Long ownerId){

        // campId 로 DB에서 조회를 시도하기 ( 기존의 getCampId)메서드를 재사용하여, 없으면 CAMP_NOT_FOUND
        Camp camp = getCampId(campId);

        // 권한 검증 : camp와 로그인 한 사람이 맞는지?
        if (!java.util.Objects.equals(camp.getOwnerId(), ownerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,"본인이 등록한 캠핑장 수정만 가능");
        }

        // 주소가 바뀌면 좌표도 다시 조회한다. DB 트랜잭션을 시작하기 전에 호출해서
        // 카카오 API 지연이 DB 커넥션을 점유하지 않도록 한다.
        GeoPoint geoPoint = request.getAddr1() != null
                ? kakaoGeocodingClient.geocode(request.getAddr1())
                : null;

        // 엔티티 메서드 호출해서 반영하기
        camp.updateInfo(request);

        // 지오코딩 실패 시 좌표는 비운다 (새 주소와 옛 좌표가 어긋난 채로 남지 않도록)
        if (request.getAddr1() != null) {
            camp.updateLocation(geoPoint);
        }

        // 트랜잭션 밖에서 조회한 뒤라 더티 체킹에 기댈 수 없으므로 명시적으로 저장한다
        return campRepository.save(camp);

    }

    // 캠핑장 삭제 기능 구현
    @Transactional
    public void deleteCamp(Long campId, Long ownerId){
        // campId 로 캠핑장을 조회하기, 없으면 CAMP_NOT_FOUND (예외)
        Camp camp = getCampId(campId);
        // 소유자 권한 검증 (ACCESS_DENIED)
        if(!java.util.Objects.equals(camp.getOwnerId(),ownerId)){
            throw new BusinessException(ErrorCode.ACCESS_DENIED,"본인이 등록한 캠핑장이 아닙니다");
        }
        // softDelete() 호출하기
        camp.softDelete();

    }

}
