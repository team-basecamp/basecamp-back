package com.basecamp.backend.domain.camp.controller;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.dto.request.CampRegistrationRequest;
import com.basecamp.backend.domain.camp.dto.request.CampUpdateRequest;
import com.basecamp.backend.domain.camp.dto.request.GocampingApiResponseDto;
import com.basecamp.backend.domain.camp.dto.response.CampDetailResponseDto;
import com.basecamp.backend.domain.camp.dto.response.CampListResponseDto;
import com.basecamp.backend.domain.camp.dto.response.CampResponseDto;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.service.CampService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.service.GenericResponseService;
import org.springframework.http.ResponseEntity;
import com.basecamp.backend.common.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final GenericResponseService responseBuilder;

    //고캠핑 API에서 캠핑장 데이터를 동기화
    @Operation(summary = "고캠핑 API 데이터 동기화",
            description = "관리자가 전달한 고캠핑 API 캠핑장 목록을 DB에 동기화합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/sync")
    public ResponseEntity<String> syncCamps(
            @RequestBody List<GocampingApiResponseDto> apiCamps) {
        campService.saveCampsFromApi(apiCamps);
        return ResponseEntity.ok("캠핑장 데이터가 성공적으로 동기화되었습니다");
    }

    // 고캠핑 API 전체를 직접 호출해서 DB에 저장 (관리자용 수동 트리거)
    // 경로가 /api/v1/admin 아래가 아니라 SecurityConfig 의 URL 규칙으로는 묶이지 않는다.
    // 메서드 하나에만 걸리는 규칙이므로 @PreAuthorize 로 보호한다.
    @Operation(summary = "고캠핑 API 전체 동기화",
            description = "관리자가 고캠핑 공공데이터 API를 직접 호출해 전체 캠핑장 데이터를 동기화합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/fetch")
    public ResponseEntity<String> fetchCamps() {
        campService.fetchAndSaveCampsFromGocampingApi();
        return ResponseEntity.ok("고캠핑 API 전체 데이터가 성공적으로 동기화되었습니다");
    }

// 특정 캠핑장 ID(PK) 로 조회 (상세페이지용 - 자체 등록 캠핑장은 contentId가 없어 이 엔드포인트로 통일 조회)
    @Operation(summary = "캠핑장 상세 조회 (campId)",
            description = "PK(campId)로 캠핑장 상세 정보를 조회합니다. 자체 등록 캠핑장처럼 contentId가 없는 경우에도 사용합니다.")
    @GetMapping("/{campId}")
    public ResponseEntity<CampDetailResponseDto> getCampById(@PathVariable Long campId) {
        Camp camp = campService.getCampId(campId);

        if (camp == null) {
            throw new BusinessException(ErrorCode.CAMP_NOT_FOUND, "캠핑장을 찾을 수 없습니다");
        }

        return ResponseEntity.ok(CampDetailResponseDto.ok(CampResponseDto.from(camp)));
    }

// 고캠핑 contentId로 캠핑장 조회 (상세페이지용)
    @Operation(summary = "캠핑장 상세 조회 (contentId)",
            description = "고캠핑 API의 contentId로 캠핑장 상세 정보를 조회합니다.")
    @GetMapping("/content/{contentId}")
    public ResponseEntity<CampDetailResponseDto> getCampByContentId(@PathVariable Long contentId) {
        Camp camp = campService.getCampByContentId(contentId);

        if (camp == null) {
            throw new BusinessException(ErrorCode.CAMP_NOT_FOUND, "캠핑장을 찾을 수 없습니다");
        }

        return ResponseEntity.ok(CampDetailResponseDto.ok(CampResponseDto.from(camp)));
    }

// 캠핑장 검색 (키워드/지역/유형/최대금액 필터 + 정렬 + 페이징 조합)
    @Operation(summary = "캠핑장 검색",
            description = "키워드/지역/유형/최대금액 필터와 정렬, 페이징을 조합해 캠핑장을 검색합니다.")
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
    @Operation(summary = "HOT 캠핑장 조회",
            description = "평점순 또는 예약건수순으로 인기 캠핑장을 조회합니다.")
    @GetMapping("/hot")
    public ResponseEntity<CampListResponseDto> getHotCamps(
            @RequestParam(required = false, defaultValue = "rating") String sortBy,
            @RequestParam(required = false, defaultValue = "1") int pageNo,
            @RequestParam(required = false, defaultValue = "10") int numOfRows) {

        return ResponseEntity.ok(campService.getHotCamps(sortBy, pageNo, numOfRows));
    }

//모든 캠핑장 조회
    @Operation(summary = "모든 캠핑장 조회",
            description = "찾아보기 탭에서 모든 캠핑장을 조회할 수 있습니다")
    @GetMapping
    public ResponseEntity<List<Camp>> getAllCamps() {
        return ResponseEntity.ok(campService.getAllCamps());
    }

// 캠핑장 이름으로 검색
    @Operation(summary = "캠핑장이름으로 검색",
            description = "검색 시 캠핑장 이름으로 검색을 하면 캠핑장 이름으로 검색이 됩니다.")
    @GetMapping("/search/name")
    public ResponseEntity<List<Camp>> searchByName(
            @RequestParam String name) {
        return ResponseEntity.ok(campService.searchByName(name));
    }

     // 특정 지역의 캠핑장을 검색
    @Operation(summary = "특정 지역 캠핑장 검색",
            description = "검색 기능으로 특정 지역캠핑장을 검색할 때 사용합니다." )
    @GetMapping("/search/address")
    public ResponseEntity<List<Camp>> searchByAddress(
            @RequestParam String address) {
        return ResponseEntity.ok(campService.searchByAddress(address));
    }

    // 업체가 등록한 캠핑장 목록 조회 ("내 캠핑장")
    // 캠핑장을 등록할 수 있는 건 CAMP_OWNER 뿐이므로(아래 /register), 조회도 같은 권한으로 맞춘다.
    // SecurityConfig 의 authenticated() 규칙은 그대로 둔다 — 비로그인은 403 이 아니라 401 이어야 한다.
    @Operation(summary = "내 캠핑장 목록 조회",
            description = "캠핑업체(CAMP_OWNER)가 등록한 캠핑장을 최근 등록순으로 조회합니다.")
    @PreAuthorize("hasRole('CAMP_OWNER')")
    @GetMapping("/my")
    public ResponseEntity<CampListResponseDto> getMyCamps(@AuthenticationPrincipal AuthUser owner) {
        return ResponseEntity.ok(campService.getMyCamps(owner.id()));
    }

    // 캠핑장 등록
    // 업체가 직접 등록하는 캠핑장(camps.owner_id). 공공데이터에서 온 캠핑장(content_id)과 배타적이다.
    // CAMP_OWNER 만 등록할 수 있다. 승격 경로는 #53 의 관리자 심사다.
    @Operation(summary = "내 캠핑장 등록 기능",
            description = "캠핑없체가 등록한 캠핑장을 최근 등록 순으로 조회 합니다.")
    @PreAuthorize("hasRole('CAMP_OWNER')")
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

    // 캠핑장 정보 수정 기능
    @Operation(summary = "캠핑장 정보 수정",
            description = "캠핑업체(CAMP_OWNER)가 본인이 등록한 캠핑장 정보를 수정합니다.")
    @PreAuthorize("hasRole('CAMP_OWNER')")
    @PatchMapping("/{campId}")
    public ResponseEntity<CampDetailResponseDto> updateCamp(
            @PathVariable
            Long campId,
            @Valid
            @RequestBody
            CampUpdateRequest request,
            @AuthenticationPrincipal
            AuthUser owner
    ){
        // Service 호출 하기
        Camp modifyCamp = campService.updateCamp(campId,request,owner.id());
        // 엔티티 -> ResponseDTO 변환
        CampResponseDto responseDto = CampResponseDto.from(modifyCamp);
        // Envelope로 감싸기
        CampDetailResponseDto response = CampDetailResponseDto.ok(responseDto);
        // 응답반환
        return ResponseEntity.ok(response);
    }

    // 캠핑장 삭제 (softDelete) controller
    @Operation(summary = "캠핑장 삭제",
            description = "캠핑업체(CAMP_OWNER)가 본인이 등록한 캠핑장을 삭제(소프트 삭제) 처리합니다.")
    @PreAuthorize("hasRole('CAMP_OWNER')")
    @DeleteMapping("/{campId}")
    public ResponseEntity<Void> deleteCamp(
            @PathVariable Long campId,
            @AuthenticationPrincipal AuthUser owner
    ){
        // Service 의 DeleteCamp() 호출하기
        campService.deleteCamp(campId, owner.id());
        // 204 No Content 로 응답 반환하기
        return ResponseEntity.noContent().build();
    }

}