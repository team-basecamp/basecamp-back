package com.basecamp.backend.domain.post.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.post.dto.request.PostUpdateRequest;
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
    public Long createPost(String category,String title, String content){
        Post post = new Post(category, title, content);
        // 엔티티 post를 저장소에 저장하고 반환값이 Post라 PK 가져오기 위해 getPostId()
        return postRepository.save(post).getPostId();
    }


    @Transactional
    public PostUpdateResponse update(Long id, PostUpdateRequest request) {
        // 수정할 게시글 조회, 없으면 예외
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        // 변경 감지로 UPDATE (트랜잭션 커밋 시점에 반영)
        post.update(request.category(), request.title(), request.content());

        return PostUpdateResponse.from(post);
    }

}
