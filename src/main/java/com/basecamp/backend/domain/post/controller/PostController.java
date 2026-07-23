package com.basecamp.backend.domain.post.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.post.dto.request.PostCreateRequest;
import com.basecamp.backend.domain.post.dto.request.PostReportRequest;
import com.basecamp.backend.domain.post.dto.request.PostUpdateRequest;
import com.basecamp.backend.domain.post.dto.response.PostDeleteResponse;
import com.basecamp.backend.domain.post.dto.response.PostDetailResponse;
import com.basecamp.backend.domain.post.dto.response.PostListCursorResponse;
import com.basecamp.backend.domain.post.dto.response.PostReportResponse;
import com.basecamp.backend.domain.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class PostController {

  // 게시글 비즈니스 로직 위임 대상
  private final PostService postService;

  // 게시글 작성: 인증 회원(userId)이 새 글을 등록하고 생성된 게시글을 반환한다.
  // multipart/form-data 로 받는다 — 본문 필드는 application/json 파트 "request", 이미지는 파일 파트 "images"(선택).
  @Operation(
      summary = "게시글 작성",
      description =
          "인증된 사용자가 새 게시글을 작성한다. multipart/form-data 로 전송하며, "
              + "'request'(application/json) 파트에 제목·내용·카테고리를, 'images' 파트에 이미지 파일들을 담는다. "
              + "이미지는 선택이며 로컬에 저장되고 응답의 imageUrls 에 상대경로(/images/파일명)로 내려간다.")
  // Swagger UI 가 'request' 파트를 text/plain 으로 보내 415 가 나는 것을 막는다.
  // @Encoding 으로 해당 파트의 Content-Type 을 application/json 으로 명시한다. (문서/Swagger 전송 형식에만 영향, 런타임 계약은
  // 그대로)
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
              encoding =
                  @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE)))
  @PostMapping(value = "/api/v1/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<PostDetailResponse> createPost(
      @AuthenticationPrincipal AuthUser user, // JWT에서 꺼낸 로그인 회원 (id, role)
      @RequestPart("request") @Valid PostCreateRequest request, // 작성 요청 본문(JSON 파트, 검증 대상)
      @RequestPart(value = "images", required = false) List<MultipartFile> images) { // 첨부 이미지(선택)
    PostDetailResponse response = postService.createPost(user.id(), request, images);
    // 생성 성공은 201 Created 로 응답
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  // 게시글 목록 조회: 카테고리로 걸러 최신순 한 페이지를 반환한다. (무한 스크롤용, 커서 페이징)
  @Operation(
      summary = "게시글 목록 조회",
      description =
          "카테고리별 게시글 목록을 최신순으로 조회한다. "
              + "category를 생략하거나 ALL로 보내면 GENERAL/CAMP_MATE/RESERVATION_TRANSFER 전체를 조회한다. "
              + "첫 페이지는 cursor 없이 요청하고, 이후에는 응답의 nextCursor를 그대로 cursor에 실어 보낸다. "
              + "hasNext가 false면 nextCursor는 null이고 더 조회할 목록이 없다.")
  @GetMapping("/api/v1/posts")
  public ResponseEntity<PostListCursorResponse> getPostList(
      // 전체 탭은 이 값을 생략하거나 ALL로 보낸다. DB에 ALL 카테고리는 없다.
      @Parameter(description = "게시판 카테고리. 생략 또는 ALL이면 전체", example = "GENERAL")
          @RequestParam(value = "category", required = false)
          String category,

      // 직전 응답의 nextCursor를 그대로 돌려보내는 자리. 첫 페이지에서는 생략한다.
      // 값을 직접 만들어 넣지 말 것 — 형식은 서버 구현 세부사항이라 언제든 바뀐다.
      @Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 생략한다.")
          @RequestParam(value = "cursor", required = false)
          String cursor,

      // 페이지당 건수. 상한 검증은 서비스에서 하고 위반 시 400.
      @Parameter(description = "페이지당 건수 (1~50)", example = "10")
          @RequestParam(value = "size", defaultValue = "10")
          int size) {

    return ResponseEntity.ok(postService.getList(category, cursor, size));
  }

  // 게시글 상세 조회: 경로의 게시글 id로 단건을 조회하고 조회수를 1 올린다.
  // 조회한 회원이 작성자 본인이면 조회수를 올리지 않는다 — 수정 폼도 이 API로 초기값을 채우기 때문이다.
  @Operation(
      summary = "게시글 상세 조회",
      description =
          "게시글 id로 단건 상세를 조회한다. 회원용 상세 조회에서는 삭제된 글은 404, 블라인드된 글은 403 으로 가려진다. "
              + "조회에 성공하면 조회수가 1 오르고, 응답의 viewCount 는 증가가 반영된 값이다. "
              + "단 작성자 본인의 조회는 조회수에 반영되지 않는다.")
  @GetMapping("/api/v1/posts/{postId}")
  public ResponseEntity<PostDetailResponse> getPostDetail(
      @AuthenticationPrincipal AuthUser user, // 조회한 회원 (인증 필수 경로라 항상 존재한다)
      @PathVariable("postId") Long postId) { // 조회할 게시글 id
    return ResponseEntity.ok(postService.getDetail(postId, user.id()));
  }

  // 게시글 수정: 경로의 게시글 id를 대상으로 작성자 본인이 내용과 첨부 이미지를 수정한다.
  // 작성과 같은 multipart/form-data 형식 — 본문 필드는 application/json 파트 "request", 새 이미지는 파일 파트
  // "images"(선택).
  @Operation(
      summary = "게시글 수정",
      description =
          "작성자 본인이 게시글의 카테고리·제목·내용과 첨부 이미지를 수정한다. "
              + "multipart/form-data 로 전송하며, 'request'(application/json) 파트에 본문 필드를, "
              + "'images' 파트에 새로 추가할 이미지 파일들을 담는다. "
              + "이미지는 전체 교체 방식이라 최종 첨부 = request.keepImageUrls(남길 기존 이미지) + images(새 파일) 이며, "
              + "keepImageUrls 에 넣지 않은 기존 이미지는 삭제된다. "
              + "keepImageUrls 를 생략하면 기존 이미지를 그대로 유지하고, 빈 배열([])을 보내면 전부 삭제한다.")
  // 작성 API와 같은 이유 — Swagger UI 가 'request' 파트를 text/plain 으로 보내 415 가 나는 것을 막는다.
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
              encoding =
                  @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE)))
  @PostMapping(
      value = "/api/v1/posts/{postId}/update",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<PostDetailResponse> updatePost(
      @AuthenticationPrincipal AuthUser user, // JWT에서 꺼낸 로그인 회원 (id, role)
      @PathVariable("postId") Long postId, // 수정할 게시글 id
      @RequestPart("request") @Valid PostUpdateRequest request, // 수정 요청 본문(JSON 파트, 검증 대상)
      @RequestPart(value = "images", required = false)
          List<MultipartFile> images) { // 새로 추가할 이미지(선택)
    // 회원 id는 토큰에서 꺼낸 user.id()만 신뢰한다. 소유권 확인은 서비스에서 한다.
    return ResponseEntity.ok(postService.update(user.id(), postId, request, images));
  }

  // 게시글 삭제: 경로의 postId를 받아 작성자 본인 글의 상태를 DELETED로 바꾸고(소프트 삭제), 첨부 이미지는 완전히 지운다.
  // 서버가 HTTP 리다이렉트를 하지 않고, 이동할 목록 경로를 응답 본문으로 내려주면 React가 라우팅한다.
  @Operation(
      summary = "게시글 삭제",
      description =
          "작성자 본인이 게시글 상태를 DELETED로 변경(소프트 삭제)한다. "
              + "글은 행이 남지만 첨부 이미지는 images 행과 실제 파일까지 되돌릴 수 없게 삭제된다. "
              + "React가 이동할 목록 경로를 반환한다.")
  @PostMapping("/api/v1/posts/{postId}/delete")
  public ResponseEntity<PostDeleteResponse> deletePost(
      @AuthenticationPrincipal AuthUser user, // JWT에서 꺼낸 로그인 회원 (id, role)
      @PathVariable("postId") Long postId) { // 삭제할 게시글 id ( 경로 변수 )
    // 회원 id는 토큰에서 꺼낸 user.id()만 신뢰한다. (요청 본문의 userId를 믿지 않는다)
    postService.delete(user.id(), postId);
    // 삭제 후 React가 게시글 목록(GET /api/v1/posts)으로 이동하도록 경로를 내려준다.
    return ResponseEntity.ok(PostDeleteResponse.toPostList());
  }

  // 게시글 신고: 경로의 postId 게시글을 로그인 회원이 신고 접수한다.     .
  @Operation(summary = "게시글 신고", description = "로그인한 사용자가 게시글을 신고한다. 접수된 신고 id와 안내 메시지를 반환한다.")
  @PostMapping("/api/v1/posts/{postId}/report")
  public ResponseEntity<PostReportResponse> reportPost(
      @AuthenticationPrincipal AuthUser user, // JWT에서 꺼낸 로그인 회원 (id, role)
      @PathVariable("postId") Long postId, // 신고할 게시글 id (경로 변수, 본문의 postId보다 우선)
      @RequestBody @Valid PostReportRequest request) { // 신고 요청 본문(검증 대상)
    // 신고자 id는 토큰에서 꺼낸 user.id()만 신뢰한다. (요청 본문의 값을 믿지 않는다)
    PostReportResponse response = postService.report(user.id(), postId, request);
    // 신고 접수 성공은 201 Created 로 응답
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
