package com.basecamp.backend.domain.camp.controller;

import com.basecamp.backend.domain.camp.dto.request.GocampingApiResponseDto;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.service.CampService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

// 특정 캠핑장 ID 검색
    @GetMapping("/{campId}")
    public ResponseEntity<?> getCampById(@PathVariable Long campId) {
        try {
            // Service의 getCampId() 메서드 호출
            Camp camp = campService.getCampId(campId);

            if (camp == null) {
                return ResponseEntity.status(404)
                        .body("캠핑장을 찾을 수 없습니다");
            }

            return ResponseEntity.ok(camp);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("조회 실패: " + e.getMessage());
        }
    }

// 고캠핑 contentId로 캠핑장 조회 (상세페이지용)
    @GetMapping("/content/{contentId}")
    public ResponseEntity<?> getCampByContentId(@PathVariable Long contentId) {
        try {
            Camp camp = campService.getCampByContentId(contentId);

            if (camp == null) {
                return ResponseEntity.status(404)
                        .body("캠핑장을 찾을 수 없습니다");
            }

            return ResponseEntity.ok(camp);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("조회 실패: " + e.getMessage());
        }
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
}