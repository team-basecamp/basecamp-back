package com.basecamp.backend.domain.post.controller;

import com.basecamp.backend.domain.post.dto.request.PostRequest;
import com.basecamp.backend.domain.post.dto.response.PostResponse;
import com.basecamp.backend.domain.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// 얘는 controller restapi를 위한거인거 앎
@RestController

// 너는 왜 있니? 그리고 생성자들 no, args, 외 차이점들은 뭘까?
@RequiredArgsConstructor
public class PostController {

    // 다음 넘겨줄 서비스 변수처리해주기
    private final PostService postService;

    // 주소는 맞나?
    @PostMapping("/api/v1/posts")
    public ResponseEntity<Long> createPost(@RequestBody PostRequest request) {
        Long postId = postService.createPost(request.title(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(postId);
    }
}