package com.basecamp.backend.domain.camp.controller;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.dto.request.CampRegistrationRequest;
import com.basecamp.backend.domain.camp.dto.request.GocampingApiResponseDto;
import com.basecamp.backend.domain.camp.dto.response.CampDetailResponseDto;
import com.basecamp.backend.domain.camp.dto.response.CampListResponseDto;
import com.basecamp.backend.domain.camp.dto.response.CampResponseDto;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.service.CampService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.basecamp.backend.common.model.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

// 캠핑장 API 앤드포인트
@RestController
@RequestMapping("/api/v1/camps")
@RequiredArgsConstructor
public class CampController {

    // Service 자동 주입
    private final CampService campService;

    //고캠핑 API에서 캠핑장 데이터를 동기화
    @PostMapping("/sync")
    public ResponseEntity<String> syncCamps(
            @RequestBody List<GocampingApiResponseDto> apiCamps) {

        try {
            // Service의 saveCampsFromApi() 메서드 호출
            campService.saveCampsFromApi(apiCamps);
            return ResponseEntity.ok("캠핑장 데이터가 성공적으로 동기화되었습니다");

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("동기화 실패: " + e.getMessage());
        }
    }

    // 고캠핑 API 전체를 직접 호출해서 DB에 저장 (관리자용 수동 트리거)
    @PostMapping("/fetch")
    public ResponseEntity<String> fetchCamps() {
        try {
            campService.fetchAndSaveCampsFromGocampingApi();
            return ResponseEntity.ok("고캠핑 API 전체 데이터가 성공적으로 동기화되었습니다");

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("동기화 실패: " + e.getMessage());
        }
    }

// 특정 캠핑장 ID(PK) 로 조회 (상세페이지용 - 자체 등록 캠핑장은 contentId가 없어 이 엔드포인트로 통일 조회)
    @GetMapping("/{campId}")
    public ResponseEntity<CampDetailResponseDto> getCampById(@PathVariable Long campId) {
        Camp camp = campService.getCampId(campId);

        if (camp == null) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "캠핑장을 찾을 수 없습니다");
        }

        return ResponseEntity.ok(CampDetailResponseDto.ok(CampResponseDto.from(camp)));
    }

// 고캠핑 contentId로 캠핑장 조회 (상세페이지용)
    @GetMapping("/content/{contentId}")
    public ResponseEntity<CampDetailResponseDto> getCampByContentId(@PathVariable Long contentId) {
        Camp camp = campService.getCampByContentId(contentId);

        if (camp == null) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "캠핑장을 찾을 수 없습니다");
        }

        return ResponseEntity.ok(CampDetailResponseDto.ok(CampResponseDto.from(camp)));
    }

// 캠핑장 검색 (키워드/지역/유형/최대금액 필터 + 정렬 + 페이징 조합)
    @GetMapping("/search")
    public ResponseEntity<CampListResponseDto> searchCamps(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String induty,
            @RequestParam(required = false) Integer priceMax,
            @RequestParam(required = false, defaultValue = "recommended") String sort,
            @RequestParam(required = false, defaultValue = "1") int pageNo,
            @RequestParam(required = false, defaultValue = "12") int numOfRows) {

        return ResponseEntity.ok(
                campService.searchCamps(keyword, region, induty, priceMax, sort, pageNo, numOfRows));
    }

// HOT 캠핑장 조회 (평점순 / 예약건수순)
    @GetMapping("/hot")
    public ResponseEntity<CampListResponseDto> getHotCamps(
            @RequestParam(required = false, defaultValue = "rating") String sortBy,
            @RequestParam(required = false, defaultValue = "1") int pageNo,
            @RequestParam(required = false, defaultValue = "10") int numOfRows) {

        return ResponseEntity.ok(campService.getHotCamps(sortBy, pageNo, numOfRows));
    }

//모든 캠핑장 조회
    @GetMapping
    public ResponseEntity<List<Camp>> getAllCamps() {
        try {
            // Service의 getAllCamps() 메서드 호출
            List<Camp> camps = campService.getAllCamps();
            return ResponseEntity.ok(camps);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

// 캠핑장 이름으로 검색
    @GetMapping("/search/name")
    public ResponseEntity<List<Camp>> searchByName(
            @RequestParam String name) {

        try {
            // Service의 searchByName() 메서드 호출
            List<Camp> camps = campService.searchByName(name);
            return ResponseEntity.ok(camps);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

     // 특정 지역의 캠핑장을 검색
    @GetMapping("/search/address")
    public ResponseEntity<List<Camp>> searchByAddress(
            @RequestParam String address) {

        try {
            // Service의 searchByAddress() 메서드 호출
            List<Camp> camps = campService.searchByAddress(address);
            return ResponseEntity.ok(camps);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // 로그인한 회원이 등록한 캠핑장 목록 조회 ("내 캠핑장")
    @Operation(summary = "내 캠핑장 목록 조회",
            description = "인증된 사용자가 등록한 캠핑장을 최근 등록순으로 조회합니다.")
    @GetMapping("/my")
    public ResponseEntity<CampListResponseDto> getMyCamps(@AuthenticationPrincipal AuthUser owner) {
        return ResponseEntity.ok(campService.getMyCamps(owner.id()));
    }

    // 캠핑장 등록
    @PostMapping("/register")
    public ResponseEntity<CampDetailResponseDto> registerCamp(
            @Valid // DTO에 붙어있는 검증 애너테이션 체크 하는 기능
            @RequestBody // 리액트가 준 JSON 형식을 Java가 이해할 수 있도록 연결 해주는 것.
            CampRegistrationRequest request,
            @AuthenticationPrincipal AuthUser owner
    ){
        // Service 호출
        Camp savedCamp = campService.registerCamp(request, owner.id());
        // Entity -> Response DTO 변환하기
        CampResponseDto responseDto = CampResponseDto.from(savedCamp);
        // Envelope로 감싸기
        CampDetailResponseDto response = CampDetailResponseDto.ok(responseDto);
        // 201 Created로 응답 반환
        return ResponseEntity.status(201).body(response);
    }
}