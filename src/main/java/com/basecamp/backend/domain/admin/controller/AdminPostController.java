package com.basecamp.backend.domain.admin.controller;

import com.basecamp.backend.domain.admin.dto.request.BlindPostRequest;
import com.basecamp.backend.domain.admin.dto.response.AdminPostDetailResponse;
import com.basecamp.backend.domain.admin.dto.response.ReportedPostResponse;
import com.basecamp.backend.domain.admin.service.AdminPostService;
import com.basecamp.backend.domain.post.entity.ReportStatus;
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
 * 관리자의 신고된 게시글 조회 및 블라인드 처리. {@code /api/v1/admin/**} 는 {@code SecurityConfig} 에서 {@code
 * ROLE_ADMIN} 만 허용한다.
 */
@Tag(name = "Admin - Post", description = "관리자 - 신고된 게시글 조회 및 블라인드 처리")
@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

  private final AdminPostService adminPostService;

  @Operation(
      summary = "신고된 게시글 목록 조회",
      description =
          "신고를 접수 최신순으로 조회한다. 신고 1건이 한 행이며 대상 게시글(제목·상태)과 신고자 정보를 함께 담는다. "
              + "status 를 생략하면 아직 처리되지 않은 PENDING 만 조회하고, ACCEPTED/REJECTED 처리 이력은 status 로 지정해 조회한다.")
  @GetMapping("/reports")
  public ResponseEntity<Page<ReportedPostResponse>> findReports(
      @Parameter(description = "신고 처리 상태 필터. 생략 시 PENDING") @RequestParam(required = false)
          ReportStatus status,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(adminPostService.findReports(status, pageable));
  }

  @Operation(
      summary = "게시글 상세 조회",
      description =
          "게시글 원문을 상태와 무관하게 조회한다. 회원용 조회와 달리 블라인드(BLINDED)·삭제(DELETED)된 글도 "
              + "그대로 열람할 수 있으며, status 와 blindReason 을 함께 담는다. 존재하지 않는 글이면 404.")
  @GetMapping("/{postId}")
  public ResponseEntity<AdminPostDetailResponse> getPostDetail(@PathVariable Long postId) {
    return ResponseEntity.ok(adminPostService.getPostDetail(postId));
  }

  @Operation(
      summary = "게시글 블라인드 처리",
      description =
          "신고된 게시글을 블라인드 상태로 바꾸고 사유를 남긴다. 블라인드된 글은 상세 조회 시 403 으로 가려진다. "
              + "이미 블라인드된 글이면 409, 없거나 삭제된 글이면 404. 처리와 함께 해당 글의 대기(PENDING) 신고는 ACCEPTED 로 정리된다.")
  @PostMapping("/{postId}/blind")
  public ResponseEntity<Void> blindPost(
      @PathVariable Long postId, @Valid @RequestBody BlindPostRequest request) {
    adminPostService.blindPost(postId, request.reason());
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "신고 반려",
      description =
          "블라인드 등 조치 없이 신고 1건을 기각한다(PENDING → REJECTED). 블라인드가 게시글 단위인 것과 달리 "
              + "반려는 신고 1건(reportId) 단위다. 이미 처리된 신고면 409, 없는 신고면 404.")
  @PostMapping("/reports/{reportId}/reject")
  public ResponseEntity<Void> rejectReport(@PathVariable Long reportId) {
    adminPostService.rejectReport(reportId);
    return ResponseEntity.noContent().build();
  }
}
