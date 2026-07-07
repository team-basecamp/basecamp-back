package com.basecamp.backend.domain.post.controller;

import com.basecamp.backend.domain.post.dto.request.PostCreateRequest;
import com.basecamp.backend.domain.post.dto.request.PostUpdateRequest;
import com.basecamp.backend.domain.post.dto.response.PostCreateResponse;
import com.basecamp.backend.domain.post.dto.response.PostUpdateResponse;
import com.basecamp.backend.domain.post.service.PostService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

// 얘는 controller restapi를 위한거인거 앎
@RestController

// 너는 왜 있니? 그리고 생성자들 no, args, 외 차이점들은 뭘까?
@RequiredArgsConstructor
public class PostController {

    // 다음 넘겨줄 서비스 변수처리해주기
    private final PostService postService;

    // 주소는 맞나?
    // valid 안써도 되ㅑㅑ냐ㅑ?
    // 게시글 작성
    @PostMapping("/api/v1/posts")
    public ResponseEntity<Long> createPost(@RequestBody PostCreateRequest request) {
        Long postId = postService.createPost(request.category(), request.title(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(postId);
    }

    // 게시판 수정
    //  PostResponse create update 따로 만드니?
//    @PostMapping("/api/v1/posts/{postId}/update")
//    public ResponseEntity<PostUpdateResponse> updatePost(
//            @PathVariable("id") Long id,
//            @RequestBody @Valid PostUpdateRequest request) {
//        return ResponseEntity.ok(postService.update(id, request));
//    }
}