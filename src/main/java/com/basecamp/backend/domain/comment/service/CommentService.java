package com.basecamp.backend.domain.comment.service;

import com.basecamp.backend.common.enums.Role;
import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.comment.dto.response.CommentResponse;
import com.basecamp.backend.domain.comment.entity.Comment;
import com.basecamp.backend.domain.comment.repository.CommentRepository;
import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.repository.PostRepository;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    Post post =
        postRepository
            .findById(postId)
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
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

    Comment comment = new Comment(post, user, content);
    // 저장한 뒤 방금 쓴 댓글을 그대로 볼 수 있게 응답 DTO로 변환해 반환.
    // post·user가 이미 로딩된 상태라 from()에서 nickname 접근 시 추가 쿼리가 발생하지 않는다.
    Comment saved = commentRepository.save(comment);
    return CommentResponse.from(saved);
  }

  // 댓글 수정: 댓글을 조회해 본인 댓글일 때만 본문을 갈아끼운 뒤 응답으로 반환한다. (쓰기 트랜잭션)
  // commentId가 전역 유니크 PK라 게시글 경로 없이 이것만으로 대상이 특정된다.
  // userId는 컨트롤러에서 토큰(AuthUser)으로부터 넘어온 값이라 신뢰할 수 있다.
  @Transactional
  public CommentResponse updateComment(Long userId, Long commentId, String content) {
    // 수정할 댓글을 작성자·프로필·게시글과 함께 조회한다. 없으면 404.
    Comment comment =
        commentRepository
            .findByIdWithUser(commentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

    // 이미 삭제/블라인드된 댓글은 수정할 수 없다. (존재 사실이 새지 않도록 없는 댓글과 동일하게 404)
    if (!comment.isActive()) {
      throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
    }

    // 노출 정책을 작성/조회와 동일하게 맞춘다. (숨김/삭제된 글의 댓글은 수정도 막는다.)
    //   DELETED : 소프트 삭제된 글. 존재 사실이 새지 않도록 없는 글과 동일하게 404.
    //   BLINDED : 관리자가 가린 글. 가려진 동안에는 댓글도 수정할 수 없으므로 403.
    // post는 findByIdWithUser에서 함께 fetch 하므로 상태 접근 시 추가 쿼리가 없다.
    Post post = comment.getPost();
    if (STATUS_DELETED.equals(post.getStatus())) {
      throw new BusinessException(ErrorCode.POST_NOT_FOUND);
    }
    if (STATUS_BLINDED.equals(post.getStatus())) {
      throw new BusinessException(ErrorCode.POST_BLINDED);
    }

    // 본인이 쓴 댓글만 수정할 수 있다. 작성자가 아니면 403.
    if (!comment.getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    // 관리 상태 엔티티라 updateContent 후 트랜잭션 커밋 시 변경 감지로 UPDATE가 나간다.
    comment.updateContent(content);
    // user·profileImage가 이미 로딩된 상태라 from()에서 nickname 접근 시 추가 쿼리가 발생하지 않는다.
    return CommentResponse.from(comment);
  }

  // 댓글 삭제(소프트 삭제): 실제 행은 지우지 않고 상태만 바꾼다. (쓰기 트랜잭션)
  //   - 작성자 본인이 삭제 → DELETED  ("사용자에 의해 삭제된 글입니다."로 대체 노출)
  //   - 관리자(ADMIN)가 삭제 → BLINDED ("관리자에 의해 삭제된 글입니다."로 대체 노출)
  //   - 둘 다 아니면(남의 댓글) → 403
  // commentId가 전역 유니크 PK라 게시글 경로 없이 이것만으로 대상이 특정된다.
  // userId·role은 컨트롤러에서 토큰(AuthUser)으로부터 넘어온 값이라 신뢰할 수 있다.
  @Transactional
  public void deleteComment(Long userId, Role role, Long commentId) {
    // 삭제할 댓글을 작성자와 함께 조회한다. 없으면 404.
    Comment comment =
        commentRepository
            .findByIdWithUser(commentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

    // 이미 삭제/블라인드된 댓글은 다시 삭제할 수 없다. (존재 사실이 새지 않도록 없는 댓글과 동일하게 404)
    if (!comment.isActive()) {
      throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
    }

    // 관리자는 어떤 댓글이든 블라인드로 가릴 수 있다. 소유권과 무관하게 관리자 권한을 우선 적용한다.
    if (role == Role.ADMIN) {
      comment.blindByAdmin();
      return;
    }

    // 관리자가 아니면 본인이 쓴 댓글만 삭제할 수 있다. 작성자가 아니면 403.
    if (!comment.getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    // 변경 감지로 status = DELETED 로 UPDATE 반영
    comment.deleteByOwner();
  }

  // 댓글 목록 조회: 대상 게시글을 검증한 뒤 그 게시글의 댓글을 작성 순으로 반환한다. (읽기 전용 트랜잭션)
  // 노출 정책은 작성(createComment)과 동일하게 맞춰, 볼 수 없는 글의 댓글도 볼 수 없게 한다.
  public List<CommentResponse> getComments(Long postId) {
    // 댓글을 조회할 게시글을 먼저 확인한다. 없으면 404.
    Post post =
        postRepository
            .findById(postId)
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
