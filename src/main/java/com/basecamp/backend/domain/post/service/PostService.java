package com.basecamp.backend.domain.post.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.post.dto.request.PostUpdateRequest;
import com.basecamp.backend.domain.post.dto.response.PostDetailResponse;
import com.basecamp.backend.domain.post.dto.response.PostUpdateResponse;
import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.repository.PostRepository;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 여기는 뭐냐 정체가 머냐
@RequiredArgsConstructor
@Transactional(readOnly = true)
// 생성자 추가해야함


@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    // 트랜잭션 확인, 추가로 널값 테스트 등 추가 할 게 없나?
    // 게시글에 뭐 추가로 이미지 정도 추가할각 생각해볼까?
    @Transactional // 이걸 붙이면 읽기 전용이 아님
    public PostDetailResponse createPost(Long userId, String category, String title, String content){
        // 작성자 회원을 먼저 조회한다. 응답에 nickname을 담아야 하므로 프록시(getReferenceById)가 아닌
        // findById로 실제 로딩하고, 존재하지 않으면 예외로 막는다.
        // (JWT는 통과했지만 탈퇴/삭제 등으로 회원이 사라졌을 수 있어 DB 존재 여부를 최종 검증한다.)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        Post post = new Post(user, category, title, content);
        // 저장한 뒤 방금 쓴 게시글을 그대로 볼 수 있게 응답 DTO로 변환해 반환.
        // user가 이미 로딩된 상태라 from()에서 nickname 접근 시 추가 쿼리가 발생하지 않는다.
        Post saved = postRepository.save(post);
        return PostDetailResponse.from(saved);
    }


    @Transactional
    public PostDetailResponse update(Long id, PostUpdateRequest request) {
        // 수정할 게시글 조회, 없으면 예외
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        // 변경 감지로 UPDATE (트랜잭션 커밋 시점에 반영)
        // 변경 감지???? ㅖㅖㅖㅖㅖㅖ?
        post.update(request.category(), request.title(), request.content());

        // 여긴 왜 dto 변환 하고 kk
        return PostDetailResponse.from(post);
    }

}
