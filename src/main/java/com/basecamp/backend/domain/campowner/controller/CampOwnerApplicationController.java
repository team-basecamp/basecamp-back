package com.basecamp.backend.domain.campowner.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.campowner.dto.request.CampOwnerApplicationRequest;
import com.basecamp.backend.domain.campowner.dto.response.CampOwnerApplicationResponse;
import com.basecamp.backend.domain.campowner.service.CampOwnerApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원의 캠핑업체 권한 승격 신청(#53).
 *
 * <p>경로가 {@code /api/v1/admin} 아래가 아니므로 {@code SecurityConfig} 의 URL 규칙으로는 권한이 좁혀지지 않는다. 신청은 일반 회원만
 * 하는 행위라 메서드에 {@code @PreAuthorize} 를 건다.
 */
@Tag(name = "CampOwner", description = "캠핑업체 권한 승격 신청")
@RestController
@RequestMapping("/api/v1/camp-owner/applications")
@RequiredArgsConstructor
public class CampOwnerApplicationController {

  private final CampOwnerApplicationService campOwnerApplicationService;

  @Operation(
      summary = "캠핑업체 전환 신청",
      description =
          "사업자 정보를 제출해 캠핑업체 권한 승격을 신청한다. 관리자가 승인해야 실제로 권한이 바뀐다. "
              + "사업자등록번호는 숫자 10자리 형식만 검증한다(실체 확인은 관리자 심사). "
              + "이미 심사 중인 신청이 있으면 409(CO002), 이미 캠핑업체면 409(CO003).")
  @PreAuthorize("hasRole('CUSTOMER')")
  @PostMapping
  public ResponseEntity<CampOwnerApplicationResponse> apply(
      @AuthenticationPrincipal AuthUser user,
      @Valid @RequestBody CampOwnerApplicationRequest request) {
    CampOwnerApplicationResponse response = campOwnerApplicationService.apply(user.id(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(
      summary = "내 신청 상태 조회",
      description =
          "본인의 가장 최근 신청 1건을 조회한다. 신청 이력이 없으면 404(CO001). "
              + "승격된 뒤에도 승인 이력을 볼 수 있어야 하므로 권한을 좁히지 않는다.")
  @GetMapping("/me")
  public ResponseEntity<CampOwnerApplicationResponse> findMyApplication(
      @AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.ok(campOwnerApplicationService.findMyLatestApplication(user.id()));
  }
}
