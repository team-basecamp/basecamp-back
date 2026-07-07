package com.basecamp.backend.domain.camp.service;

import com.basecamp.backend.domain.camp.dto.request.GocampingApiResponseDto;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;  // ← 추가!
import org.springframework.http.ResponseEntity;  // ← 추가!
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;  // ← 추가!

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
            int pageNo = 1;  //  첫 번째 페이지부터 시작
            int numOfRows = 100;  // 한 번에 100개씩 받기
            boolean hasMoreData = true;  //  "더 받을 데이터가 있나?" 플래그

            int totalSaved = 0;  //  저장된 총 개수 추적

            // 더 받을 데이터가 있으면 계속 반복
            while (hasMoreData) {
                // API URL 구성 (페이지 번호 포함)
                String url = gocampingApiUrl + "?serviceKey=" + gocampingApiKey
                        + "&numOfRows=" + numOfRows
                        + "&pageNo=" + pageNo  // ← 페이지 번호 변경
                        + "&MobileOS=ETC&MobileApp=basecamp&_type=json";

                System.out.println("고캠핑 API 호출 중... (페이지: " + pageNo + ")");

                // API 호출
                ResponseEntity<GocampingApiResponse> response =
                        restTemplate.getForEntity(url, GocampingApiResponse.class);

                // 응답 처리
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    ResponseBody responseBody = response.getBody().getResponse();
                    Body body = responseBody != null ? responseBody.getBody() : null;
                    Items items = body != null ? body.getItems() : null;
                    List<GocampingApiResponseDto> camps = items != null ? items.getItem() : null;

                    System.out.println("========== 고캠핑 API 응답 데이터 ==========");
                    System.out.println("페이지: " + pageNo);
                    System.out.println("받은 캠핑장 개수: " + camps.size());
                    System.out.println("\n--- 첫 번째 캠핑장 데이터 ---");
                    if (!camps.isEmpty()) {
                        GocampingApiResponseDto firstCamp = camps.get(0);
                        System.out.println("contentId: " + firstCamp.getContentId());
                        System.out.println("facltNm: " + firstCamp.getFacltNm());
                        System.out.println("addr1: " + firstCamp.getAddr1());
                        System.out.println("mapX: " + firstCamp.getMapX());
                        System.out.println("mapY: " + firstCamp.getMapY());
                        System.out.println("tel: " + firstCamp.getTel());
                        System.out.println("induty: " + firstCamp.getInduty());
                        System.out.println("gnrlSiteCo: " + firstCamp.getGnrlSiteCo());
                        System.out.println("autoSiteCo: " + firstCamp.getAutoSiteCo());
                        System.out.println("glampSiteCo: " + firstCamp.getGlampSiteCo());
                        System.out.println("firstImageUrl: " + firstCamp.getFirstImageUrl());
                        System.out.println("manageSttus: " + firstCamp.getManageSttus());
                    }
                    System.out.println("=======================================\n");

                    // 5️⃣ 받은 데이터가 없으면 종료
                    if (camps == null || camps.isEmpty()) {
                        System.out.println("✅ 모든 페이지를 받았습니다");
                        hasMoreData = false;  // 반복 종료
                    } else {
                        // 6️⃣ DB에 저장
                        int savedCount = camps.size();
                        saveCampsFromApi(camps);

                        totalSaved += savedCount;
                        System.out.println("✅ 페이지 " + pageNo + ": " + savedCount + "개 저장됨");

                        // 7️⃣ 받은 데이터가 numOfRows보다 적으면 마지막 페이지
                        if (savedCount < numOfRows) {
                            System.out.println("✅ 마지막 페이지입니다");
                            hasMoreData = false;  // 반복 종료
                        }

                        // 8️⃣ 다음 페이지로 이동
                        pageNo++;
                    }
                } else {
                    throw new RuntimeException("API 응답이 비정상입니다");
                }
            }

            System.out.println("🎉 총 " + totalSaved + "개의 캠핑장이 저장되었습니다");

        } catch (Exception e) {
            System.out.println("❌ 고캠핑 API 호출 실패: " + e.getMessage());
            throw new RuntimeException("고캠핑 API 호출 실패", e);
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

}
