package com.basecamp.backend.domain.comment.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.comment.dto.response.CommentResponse;
import com.basecamp.backend.domain.comment.entity.Comment;
import com.basecamp.backend.domain.comment.repository.CommentRepository;
import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.repository.PostRepository;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 댓글 비즈니스 로직. 기본은 읽기 전용 트랜잭션, 쓰기 메서드에만 @Transactional을 따로 건다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    // 게시글 상태값 (posts.status: ACTIVE / BLINDED / DELETED)
    private static final String STATUS_BLINDED = "BLINDED";
    private static final String STATUS_DELETED = "DELETED";

    // 댓글 저장/조회 리포지토리
    private final CommentRepository commentRepository;
    // 대상 게시글 조회 리포지토리
    private final PostRepository postRepository;
    // 작성자 회원 조회 리포지토리
    private final UserRepository userRepository;

    // 댓글 작성: 대상 게시글과 작성자를 검증한 뒤 새 댓글을 저장하고 응답으로 반환한다. (쓰기 트랜잭션)
    // userId는 컨트롤러에서 토큰(AuthUser)으로부터 넘어온 값이라 신뢰할 수 있다.
    @Transactional
    public CommentResponse createComment(Long userId, Long postId, String content) {
        // 댓글을 달 게시글을 먼저 조회한다. 없으면 404.
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 노출 정책을 상세 조회와 일치시킨다.
        //   DELETED : 소프트 삭제된 글. 존재 사실이 새지 않도록 없는 글과 동일하게 404.
        //   BLINDED : 관리자가 가린 글. 글이 가려진 동안에는 댓글도 달 수 없으므로 403.
        if (STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (STATUS_BLINDED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.POST_BLINDED);
        }

        // 작성자 회원을 조회한다. 응답에 nickname을 담아야 하므로 프록시(getReferenceById)가 아닌
        // findById로 실제 로딩하고, 존재하지 않으면 예외로 막는다.
        // (JWT는 통과했지만 탈퇴/삭제 등으로 회원이 사라졌을 수 있어 DB 존재 여부를 최종 검증한다.)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        Comment comment = new Comment(post, user, content);
        // 저장한 뒤 방금 쓴 댓글을 그대로 볼 수 있게 응답 DTO로 변환해 반환.
        // post·user가 이미 로딩된 상태라 from()에서 nickname 접근 시 추가 쿼리가 발생하지 않는다.
        Comment saved = commentRepository.save(comment);
        return CommentResponse.from(saved);
    }

    // 댓글 목록 조회: 대상 게시글을 검증한 뒤 그 게시글의 댓글을 작성 순으로 반환한다. (읽기 전용 트랜잭션)
    // 노출 정책은 작성(createComment)과 동일하게 맞춰, 볼 수 없는 글의 댓글도 볼 수 없게 한다.
    public List<CommentResponse> getComments(Long postId) {
        // 댓글을 조회할 게시글을 먼저 확인한다. 없으면 404.
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 상세/작성과 동일한 노출 정책.
        //   DELETED : 소프트 삭제된 글. 존재 사실이 새지 않도록 없는 글과 동일하게 404.
        //   BLINDED : 관리자가 가린 글. 가려진 동안에는 댓글도 볼 수 없으므로 403.
        if (STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (STATUS_BLINDED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.POST_BLINDED);
        }

        // 작성자·프로필 이미지를 fetch join으로 함께 로딩해 N+1 없이 응답 DTO로 변환한다.
        // 댓글이 없으면 빈 리스트가 그대로 반환된다.
        return commentRepository.findByPostIdWithUser(postId).stream()
                .map(CommentResponse::from)
                .toList();
    }
}
