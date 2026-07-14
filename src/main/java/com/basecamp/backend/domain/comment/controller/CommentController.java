package com.basecamp.backend.domain.comment.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.comment.dto.request.CommentCreateRequest;
import com.basecamp.backend.domain.comment.dto.response.CommentResponse;
import com.basecamp.backend.domain.comment.service.CommentService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CommentController {

    // 댓글 비즈니스 로직 위임 대상
    private final CommentService commentService;

    // 댓글 작성: 인증 회원(userId)이 경로의 게시글(postId)에 새 댓글을 등록하고 생성된 댓글을 반환한다.
    @Operation(summary = "댓글 작성", description = "인증된 사용자가 게시글에 새 댓글을 작성한다.")
    @PostMapping("/api/v1/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @AuthenticationPrincipal AuthUser user,             // JWT에서 꺼낸 로그인 회원 (id, role)
            @PathVariable("postId") Long postId,                // 댓글을 달 게시글 id (경로 변수)
            @RequestBody @Valid CommentCreateRequest request) { // 작성 요청 본문(검증 대상)
        // 회원 id는 토큰에서 꺼낸 user.id()만 신뢰한다. (요청 본문의 userId를 믿지 않는다)
        CommentResponse response = commentService.createComment(user.id(), postId, request.content());
        // 생성 성공은 201 Created 로 응답
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
