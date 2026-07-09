package com.basecamp.backend.domain.camp.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.dto.request.CampRegistrationRequest;
import com.basecamp.backend.domain.camp.dto.request.GocampingApiResponseDto;
import com.basecamp.backend.domain.camp.dto.response.CampListResponseDto;
import com.basecamp.backend.domain.camp.dto.response.CampResponseDto;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.camp.repository.CampSpecs;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.stream.Collectors;

@Slf4j // 실제 로깅 도구를 감싸는 창구/인터페이스, 이 클래스에서 로그 찍을 수 있는 로그는 변수를 자동으로 만들어 주는 애너테이션
@Service
@RequiredArgsConstructor
public class CampService {

    // campRepository 를 자동으로 주입 받기
    private final CampRepository campRepository;
    private final RestTemplate restTemplate;

    @Value("${gocamping.api.key}")
    private String gocampingApiKey;

    @Value("${gocamping.api.url}")
    private String gocampingApiUrl;

    // 고캠핑 API 에서 받은 캠핑장 데이터 DB 저장
    @Transactional
    public void saveCampsFromApi(List<GocampingApiResponseDto> apiCamps){
        // API 에서 받은 데이터가 없다면 ? 메서드 종료
        if (apiCamps == null || apiCamps.isEmpty()){
            return;
        }
        // DB 에 이미 저장이 된 contentId를 모두 가져오기
        Set<Long> existingContentIds = campRepository.findAllContentIds()
                .stream()
                .collect(Collectors.toSet());

        // 새로운 데이터만 필터링 하고 Entity로 변환 하기
        List<Camp> newCamps = apiCamps.stream()
                .filter(dto -> !existingContentIds.contains(dto.getContentId()))
                .map(Camp::fromGocampingApi)
                .collect(Collectors.toList());
        // 새로운 데이터 DB 저장 로직
        if(!newCamps.isEmpty()){
            campRepository.saveAll(newCamps);
        }
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

    // 특정 캠핑장 ID 조회 (Read)
    public Camp getCampId(Long campId){
        return campRepository.findById(campId)
                .orElse(null); // 없다면 null을 반환하라는 것
    }

    // 고캠핑 API의 contentId로 캠핑장 조회 (Read)
    public Camp getCampByContentId(Long contentId){
        return campRepository.findByContentId(contentId)
                .orElse(null);
    }

    // 모든 캠핑장 조회
    public List <Camp> getAllCamps(){
        return campRepository.findAll();
    }

   // 캠핑장 이름으로 검색
   public List<Camp> searchByName(String name) {
       return campRepository.findByFacltNmContaining(name);
   }

   //특정 지역 캠핑장 검색
   public List<Camp> searchByAddress(String address) {
       return campRepository.findByAddr1Containing(address);
   }

    // 키워드/지역/유형/최대금액 필터 + 정렬 + 페이징을 조합한 캠핑장 목록/상세검색 조회
    // sort: recommended(기본, 평점->예약건수순) / rating(평점순) / reviewCount(리뷰많은순) / priceAsc(가격낮은순) / recent(최근등록순)
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

    // 캠핑장 등록 비지니스 로직
    @Transactional
    public Camp registerCamp(CampRegistrationRequest request,Long ownerId){
        // 권한 검증 : ownerId가 없다면 등록이 불가하도록 설정
        // ownerId == null : 인증 정보 자체가 없는 것 ( 로그인을 안함, 토큰이 없음 )
        // ownerId <= 0 : 이상한 값 ( 있을 수 없는 ID )
        if(ownerId == null || ownerId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED,"캠핑장 등록 권한이 없습니다.");
        }

        // DTO -> Entity 변환 하는 코드
        Camp camp = Camp.builder()
                .facltNm(request.getFacltNm())
                .addr1(request.getAddr1())
                .addr2(request.getAddr2())
                .tel(request.getTel())
                .induty(request.getInduty())
                .price(request.getPrice())
                .gnrlSiteCo(request.getGnrlSiteCo() != null ? request.getGnrlSiteCo() : 0)
                .autoSiteCo(request.getAutoSiteCo() != null ? request.getAutoSiteCo() : 0)
                .glampSiteCo(request.getGlampSiteCo() != null ? request.getGlampSiteCo() : 0)
                .lineIntro(request.getLineIntro())
                .firstImageUrl(request.getFirstImageUrl())
                .ownerId(ownerId)
                .contentId(null)
                .manageSttus("운영")
                .averageRating(new BigDecimal("0.00"))
                .reservationCount(0)
                .createdAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")))
                .build();

        // DB에  camping 장 저장
        return campRepository.save(camp);

    }

}
