package com.basecamp.backend.domain.admin.controller;

import com.basecamp.backend.common.enums.Role;
import com.basecamp.backend.domain.admin.dto.request.BlacklistUserRequest;
import com.basecamp.backend.domain.admin.dto.response.AdminUserResponse;
import com.basecamp.backend.domain.admin.dto.response.BlacklistedUserResponse;
import com.basecamp.backend.domain.admin.service.AdminUserService;
import com.basecamp.backend.domain.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자의 회원 조회 및 제재 관리. {@code /api/v1/admin/**} 는 {@code SecurityConfig} 에서 {@code ROLE_ADMIN} 만
 * 허용한다.
 */
@Tag(name = "Admin - User", description = "관리자 - 회원 목록 조회 및 제재(강제 로그아웃)")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

  private final AdminUserService adminUserService;

  @Operation(
      summary = "회원 목록 조회",
      description =
          "회원을 가입 최신순으로 조회한다. status · role · keyword 는 모두 선택이며, 지정한 것만 AND 로 묶인다. "
              + "필터가 없으면 탈퇴 회원까지 포함한 전체가 조회된다. keyword 는 닉네임 또는 이메일 부분 일치.")
  @GetMapping
  public ResponseEntity<Page<AdminUserResponse>> findUsers(
      @Parameter(description = "회원 상태 필터") @RequestParam(required = false) UserStatus status,
      @Parameter(description = "권한 필터") @RequestParam(required = false) Role role,
      @Parameter(description = "닉네임 또는 이메일 검색어") @RequestParam(required = false) String keyword,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(adminUserService.findUsers(status, role, keyword, pageable));
  }

  @Operation(
      summary = "회원 제재(강제 로그아웃)",
      description =
          "회원을 제재 상태로 바꾸고 이미 발급된 access token 을 즉시 무효화한다. "
              + "제재 중에는 access token 인증 · 토큰 재발급 · 소셜 재로그인이 모두 차단된다. "
              + "이미 제재된 회원이면 409, 없거나 탈퇴한 회원이면 404.")
  @PostMapping("/{userId}/blacklist")
  public ResponseEntity<Void> blacklistUser(
      @PathVariable Long userId, @Valid @RequestBody BlacklistUserRequest request) {
    adminUserService.blacklistUser(userId, request.reason());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "제재된 회원 목록 조회", description = "제재 상태인 회원을 제재 일시 최신순으로 조회한다.")
  @GetMapping("/blacklist")
  public ResponseEntity<Page<BlacklistedUserResponse>> findBlacklistedUsers(
      @ParameterObject
          @PageableDefault(size = 20, sort = "blacklistedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(adminUserService.findBlacklistedUsers(pageable));
  }

  @Operation(
      summary = "회원 제재 해제",
      description =
          "제재를 해제하고 무효화 표시를 지운다. 제재 중에는 토큰이 발급되지 않았으므로 사용자는 다시 로그인해야 한다. " + "제재 상태가 아닌 회원이면 409.")
  @PostMapping("/{userId}/blacklist/release")
  public ResponseEntity<Void> releaseUser(@PathVariable Long userId) {
    adminUserService.releaseUser(userId);
    return ResponseEntity.noContent().build();
  }
}
