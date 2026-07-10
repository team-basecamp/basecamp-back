package com.basecamp.backend.domain.post.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.post.dto.request.PostCreateRequest;
import com.basecamp.backend.domain.post.dto.request.PostUpdateRequest;
import com.basecamp.backend.domain.post.dto.response.PostDetailResponse;
import com.basecamp.backend.domain.post.service.PostService;

import io.swagger.v3.oas.annotations.Operation;
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

    // 게시글 수정: 경로의 게시글 id를 대상으로 내용을 수정한다.
    @Operation(summary = "게시글 수정", description = "게시글의 카테고리·제목·내용을 수정한다.")
    @PostMapping("/api/v1/posts/{postId}/update")
    public ResponseEntity<PostDetailResponse> updatePost(
            @PathVariable("postId") Long id,               // 수정할 게시글 id
            @RequestBody @Valid PostUpdateRequest request) {  // 수정 요청 본문(검증 대상)
        return ResponseEntity.ok(postService.update(id, request));
    }
}