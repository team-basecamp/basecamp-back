package com.basecamp.backend.domain.post.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.post.dto.request.PostCreateRequest;
import com.basecamp.backend.domain.post.dto.request.PostUpdateRequest;
import com.basecamp.backend.domain.post.dto.response.PostDetailResponse;
import com.basecamp.backend.domain.post.dto.response.PostListCursorResponse;
import com.basecamp.backend.domain.post.service.PostService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PostController {

    // 게시글 비즈니스 로직 위임 대상
    private final PostService postService;

    // 게시글 작성: 인증 회원(userId)이 새 글을 등록하고 생성된 게시글을 반환한다.
    @Operation(summary = "게시글 작성", description = "인증된 사용자가 새 게시글을 작성한다.")
    @PostMapping("/api/v1/posts")
    public ResponseEntity<PostDetailResponse> createPost(
            @AuthenticationPrincipal AuthUser user,           // JWT에서 꺼낸 로그인 회원 (id, role)
            @RequestBody @Valid PostCreateRequest request) {  // 작성 요청 본문(검증 대상)
        PostDetailResponse response = postService.createPost(user.id(), request.category(), request.title(), request.content());
        // 생성 성공은 201 Created 로 응답
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 게시글 목록 조회: 카테고리로 걸러 최신순 한 페이지를 반환한다. (무한 스크롤용, 커서 페이징)
    @Operation(
            summary = "게시글 목록 조회",
            description = "카테고리별 게시글 목록을 최신순으로 조회한다. "
                    + "category를 생략하거나 ALL로 보내면 GENERAL/CAMP_MATE/RESERVATION_TRANSFER 전체를 조회한다. "
                    + "첫 페이지는 cursor 없이 요청하고, 이후에는 응답의 nextCursor를 그대로 cursor에 실어 보낸다. "
                    + "hasNext가 false면 nextCursor는 null이고 더 조회할 목록이 없다."
    )
    @GetMapping("/api/v1/posts")
    public ResponseEntity<PostListCursorResponse> getPostList(
            // 전체 탭은 이 값을 생략하거나 ALL로 보낸다. DB에 ALL 카테고리는 없다.
            @Parameter(description = "게시판 카테고리. 생략 또는 ALL이면 전체", example = "GENERAL")
            @RequestParam(value = "category", required = false) String category,

            // 직전 응답의 nextCursor를 그대로 돌려보내는 자리. 첫 페이지에서는 생략한다.
            // 값을 직접 만들어 넣지 말 것 — 형식은 서버 구현 세부사항이라 언제든 바뀐다.
            @Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 생략한다.")
            @RequestParam(value = "cursor", required = false) String cursor,

            // 페이지당 건수. 상한 검증은 서비스에서 하고 위반 시 400.
            @Parameter(description = "페이지당 건수 (1~50)", example = "10")
            @RequestParam(value = "size", defaultValue = "10") int size) {

        return ResponseEntity.ok(postService.getList(category, cursor, size));
    }

    // 게시글 상세 조회: 경로의 게시글 id로 단건을 조회한다.
    @Operation(summary = "게시글 상세 조회", description = "게시글 id로 단건 상세를 조회한다. 삭제된 글은 404, 블라인드된 글은 403으로 응답한다.")
    @GetMapping("/api/v1/posts/{postId}")
    public ResponseEntity<PostDetailResponse> getPostDetail(
            @PathVariable("postId") Long postId) {   // 조회할 게시글 id
        return ResponseEntity.ok(postService.getDetail(postId));
    }

    // 게시글 수정: 경로의 게시글 id를 대상으로 내용을 수정한다.
    @Operation(summary = "게시글 수정", description = "게시글의 카테고리·제목·내용을 수정한다.")
    @PostMapping("/api/v1/posts/{postId}/update")
    public ResponseEntity<PostDetailResponse> updatePost(
            @PathVariable("postId") Long id,               // 수정할 게시글 id
            @RequestBody @Valid PostUpdateRequest request) {  // 수정 요청 본문(검증 대상)
        return ResponseEntity.ok(postService.update(id, request));
    }
}