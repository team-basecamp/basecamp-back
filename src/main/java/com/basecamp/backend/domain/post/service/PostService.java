package com.basecamp.backend.domain.post.service;

import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 여기는 뭐냐 정체가 둘이
@RequiredArgsConstructor
@Transactional(readOnly = true)
// 생성자 추가해야함


@Service
public class PostService {

    private final PostRepository postRepository;

    // 트랜잭션 확인, 추가로 널값 테스트 등 추가 할 게 없나?
    // 게시글에 뭐 추가로 이미지 정도 추가할각 생각해볼까?
    @Transactional // 이걸 붙이면 읽기 전용이 아님
    public Long createPost(String title, String content){
        Post post = new Post(title, content);
        return postRepository.save(post).getPostId();
    }

}
