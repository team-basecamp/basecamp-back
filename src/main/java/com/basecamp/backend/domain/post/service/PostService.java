package com.basecamp.backend.domain.post.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.post.dto.request.PostUpdateRequest;
import com.basecamp.backend.domain.post.dto.response.PostCreateResponse;
import com.basecamp.backend.domain.post.dto.response.PostUpdateResponse;
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
    public PostCreateResponse createPost(String category,String title, String content){
        Post post = new Post(category, title, content);
        // 저장한 뒤 방금 쓴 게시글을 그대로 볼 수 있게 응답 DTO로 변환해 반환
        Post saved = postRepository.save(post);
        return PostCreateResponse.from(saved);
    }


    @Transactional
    public PostUpdateResponse update(Long id, PostUpdateRequest request) {
        // 수정할 게시글 조회, 없으면 예외
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        // 변경 감지로 UPDATE (트랜잭션 커밋 시점에 반영)
        // 변경 감지????
        post.update(request.category(), request.title(), request.content());

        // 여긴 왜 dto 변환 하고 kk
        return PostUpdateResponse.from(post);
    }

}
