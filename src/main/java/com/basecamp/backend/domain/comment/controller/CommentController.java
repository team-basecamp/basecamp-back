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

import java.util.List;

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

    // 댓글 목록 조회: 경로의 게시글(postId)에 달린 댓글을 작성 순으로 반환한다.
    // 작성자 식별·본인 여부 표시는 프론트에서 처리하므로 별도 인증 정보는 받지 않는다.
    // (게시판은 SecurityConfig의 anyRequest().authenticated()로 로그인 사용자만 접근 가능하다.)
    @Operation(summary = "댓글 목록 조회", description = "게시글에 달린 댓글을 작성 순으로 조회한다.")
    @GetMapping("/api/v1/posts/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(
            @PathVariable("postId") Long postId) {        // 댓글을 조회할 게시글 id (경로 변수)
        // 게시글 존재·노출 정책 검증은 서비스가 담당한다. (없는 글 404, 블라인드 403)
        List<CommentResponse> responses = commentService.getComments(postId);
        // 조회 성공은 200 OK. 댓글이 없으면 빈 배열([])을 반환한다.
        return ResponseEntity.ok(responses);
    }
}
