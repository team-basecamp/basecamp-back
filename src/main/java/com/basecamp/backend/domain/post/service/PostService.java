package com.basecamp.backend.domain.post.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.post.dto.request.PostUpdateRequest;
import com.basecamp.backend.domain.post.dto.response.PostDetailResponse;
import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.repository.PostRepository;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 게시글 비즈니스 로직. 기본은 읽기 전용 트랜잭션, 쓰기 메서드에만 @Transactional을 따로 건다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    // 게시글 저장/조회 리포지토리
    private final PostRepository postRepository;
    // 작성자 회원 조회 리포지토리
    private final UserRepository userRepository;

    // 게시글 작성: 작성자를 검증한 뒤 새 글을 저장하고 상세 응답으로 반환한다. (쓰기 트랜잭션)
    @Transactional
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


    // 게시글 수정: 대상 글을 조회해 내용을 바꾸고 상세 응답으로 반환한다. (쓰기 트랜잭션)
    @Transactional
    public PostDetailResponse update(Long id, PostUpdateRequest request) {
        // 수정할 게시글 조회, 없으면 예외
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        // 변경 감지(dirty checking)로 트랜잭션 커밋 시점에 UPDATE 반영
        post.update(request.category(), request.title(), request.content());

        return PostDetailResponse.from(post);
    }

}
