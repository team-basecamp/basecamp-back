package com.basecamp.backend.domain.admin.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.admin.dto.request.RejectApplicationRequest;
import com.basecamp.backend.domain.admin.service.AdminCampOwnerService;
import com.basecamp.backend.domain.campowner.dto.response.CampOwnerApplicationResponse;
import com.basecamp.backend.domain.campowner.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자의 캠핑업체 권한 승격 심사. {@code /api/v1/admin/**} 는 {@code SecurityConfig} 에서 {@code ROLE_ADMIN} 만
 * 허용하므로 메서드에 별도 권한 애너테이션을 붙이지 않는다.
 */
@Tag(name = "Admin - CampOwner", description = "관리자 - 캠핑업체 권한 승격 심사")
@RestController
@RequestMapping("/api/v1/admin/camp-owner/applications")
@RequiredArgsConstructor
public class AdminCampOwnerController {

  private final AdminCampOwnerService adminCampOwnerService;

  @Operation(
      summary = "업체 전환 신청 목록 조회",
      description = "심사 상태별로 신청을 조회한다. 기본은 심사 중(PENDING), 신청 일시 최신순.")
  @GetMapping
  public ResponseEntity<Page<CampOwnerApplicationResponse>> findApplications(
      @RequestParam(defaultValue = "PENDING") ApplicationStatus status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(adminCampOwnerService.findApplications(status, pageable));
  }

  @Operation(
      summary = "업체 전환 신청 승인",
      description =
          "신청을 승인하고 회원을 CAMP_OWNER 로 승격한다. 이미 발급된 access token 은 즉시 무효화되므로 "
              + "사용자는 다시 로그인해야 새 권한을 받는다. "
              + "신청이 없으면 404(CO001), 이미 처리된 신청이면 409(CO004), 이미 캠핑업체면 409(CO003).")
  @PostMapping("/{applicationId}/approve")
  public ResponseEntity<Void> approve(
      @PathVariable Long applicationId, @AuthenticationPrincipal AuthUser admin) {
    adminCampOwnerService.approve(applicationId, admin.id());
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "업체 전환 신청 반려",
      description =
          "신청을 반려한다. 회원 권한은 그대로이므로 토큰도 그대로 유효하다. " + "반려된 회원은 다시 신청할 수 있다. 이미 처리된 신청이면 409(CO004).")
  @PostMapping("/{applicationId}/reject")
  public ResponseEntity<Void> reject(
      @PathVariable Long applicationId,
      @AuthenticationPrincipal AuthUser admin,
      @Valid @RequestBody RejectApplicationRequest request) {
    adminCampOwnerService.reject(applicationId, admin.id(), request.reason());
    return ResponseEntity.noContent().build();
  }
}
