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

// requestMapping 쓰면 공통 주소 정할 수 있음.
// 너는 왜 있니? 그리고 생성자들 no, args, 외 차이점들은 뭘까?
@RequiredArgsConstructor
public class PostController {

    // 다음 넘겨줄 서비스 변수처리해주기
    private final PostService postService;


    // valid 안써도 되ㅑㅑ냐ㅑ?

    // 카테고리에 General 외 3개 내에서만 선택할 수 있게
    // 아니 id 반환하면 안될거 같은디
    // 게시글 작성
    @PostMapping("/api/v1/posts")
    public ResponseEntity<PostCreateResponse> createPost(@RequestBody @Valid PostCreateRequest request) {
        PostCreateResponse response = postService.createPost(request.category(), request.title(), request.content());
        // ok는 200이고 created는 201이라 표준임, craeted만 저거도 나머지는 다 ok써도 됨( 단 예외처리해줘야함 ).
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 게시판 수정
    // PostResponse create update 따로 만드니? -> ㅇㅇㅇ
    @PostMapping("/api/v1/posts/{postId}/update")
    public ResponseEntity<PostUpdateResponse> updatePost(
            @PathVariable("postId") Long id,
            @RequestBody @Valid PostUpdateRequest request) {
        return ResponseEntity.ok(postService.update(id, request));
    }
}