package com.basecamp.backend.domain.post.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.comment.dto.PostCommentCount;
import com.basecamp.backend.domain.comment.repository.CommentRepository;
import com.basecamp.backend.domain.post.dto.request.PostCursorRequest;
import com.basecamp.backend.domain.post.dto.request.PostUpdateRequest;
import com.basecamp.backend.domain.post.dto.request.PostReportRequest;
import com.basecamp.backend.domain.post.dto.response.PostDetailResponse;
import com.basecamp.backend.domain.post.dto.response.PostListCursorResponse;
import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.entity.PostReport;
import com.basecamp.backend.domain.post.repository.PostReportRepository;
import com.basecamp.backend.domain.post.repository.PostRepository;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 게시글 비즈니스 로직. 기본은 읽기 전용 트랜잭션, 쓰기 메서드에만 @Transactional을 따로 건다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    // 게시글 상태값 (posts.status: ACTIVE / BLINDED / DELETED)
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_BLINDED = "BLINDED";
    private static final String STATUS_DELETED = "DELETED";

    // 게시판 카테고리. DB에 저장되는 값은 이 3개뿐이다.
    private static final Set<String> CATEGORIES = Set.of("GENERAL", "CAMP_MATE", "RESERVATION_TRANSFER");

    // "전체" 탭을 위한 값. DB에 ALL이라는 카테고리를 만들지 않고, 카테고리 조건 자체를 빼는 것으로 처리한다.
    private static final String CATEGORY_ALL = "ALL";

    // 한 번에 내려줄 수 있는 게시글 수 상한.
    // size는 클라이언트가 마음대로 넣는 값이라 막지 않으면 size=100000 한 방으로 테이블을 통째로 퍼갈 수 있다.
    private static final int MAX_PAGE_SIZE = 50;

    // 게시글 저장/조회 리포지토리
    private final PostRepository postRepository;
    // 작성자 회원 조회 리포지토리
    private final UserRepository userRepository;
    // 게시글 신고 저장/조회 리포지토리
    private final PostReportRepository postReportRepository;
    // 목록의 댓글 수 집계용 리포지토리 (post_id 단위 GROUP BY COUNT)
    private final CommentRepository commentRepository;

    // 게시글 목록 조회: 카테고리로 걸러 최신순 한 페이지를 반환한다. (클래스 기본 readOnly 트랜잭션)
    // category가 없거나 ALL이면 3개 카테고리 전부, 즉 카테고리 조건을 걸지 않은 결과를 준다.
    // 목록에는 ACTIVE만 노출한다. 삭제된 글은 물론이고, 블라인드된 글도 제목이 남으면 가린 의미가 없다.
    //
    // 커서 페이징이다. cursor가 없으면 첫 페이지, 있으면 그 커서 "다음"부터 size건을 준다.
    public PostListCursorResponse getList(String category, String cursor, int size) {
        String filter = resolveCategory(category);
        int limit = resolveSize(size);
        PostCursorRequest decoded = PostCursorRequest.decode(cursor);

        // 다음 페이지 존재 여부를 알아내려고 한 건 더 조회한다. 초과분은 응답 DTO가 잘라낸다.
        List<Post> lookahead = findPage(filter, decoded, Limit.of(limit + 1));

        // 이 페이지 게시글들의 댓글 수를 한 번에 집계한다. (게시글마다 count를 날리면 N+1)
        Map<Long, Integer> commentCounts = countCommentsByPost(lookahead);

        return PostListCursorResponse.of(lookahead, limit, commentCounts);
    }

    // 목록에 실린 게시글들의 댓글 수를 post_id → count 맵으로 만든다.
    // lookahead 전체(초과분 1건 포함)의 post_id로 집계하지만, 응답에 안 쓰이는 그 한 건은 버려질 뿐 문제 없다.
    // 댓글이 없는 게시글은 집계 결과에 아예 없으므로 맵에도 키가 없고, 조회측에서 0으로 채운다.
    private Map<Long, Integer> countCommentsByPost(List<Post> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }

        List<Long> postIds = posts.stream()
                .map(Post::getPostId)
                .toList();

        return commentRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        PostCommentCount::postId,
                        row -> Math.toIntExact(row.count())));
    }

    // 카테고리 유무 · 커서 유무 4가지 조합을 각 전용 쿼리로 보낸다. (사유는 PostRepository 주석 참고)
    private List<Post> findPage(String category, PostCursorRequest cursor, Limit limit) {
        if (category == null) {
            return (cursor == null)
                    ? postRepository.findFirstPage(STATUS_ACTIVE, limit)
                    : postRepository.findNextPage(STATUS_ACTIVE, cursor.createdAt(), cursor.postId(), limit);
        }

        return (cursor == null)
                ? postRepository.findFirstPageByCategory(STATUS_ACTIVE, category, limit)
                : postRepository.findNextPageByCategory(STATUS_ACTIVE, category, cursor.createdAt(), cursor.postId(), limit);
    }





//    // 게시글 상세 조회: 단건을 조회해 상세 응답으로 반환한다. (클래스 기본 readOnly 트랜잭션)
//    //
//    // 노출 정책 — posts.status에 따라 갈린다.
//    //   ACTIVE  : 정상 반환
//    //   DELETED : 소프트 삭제된 글. 없는 글과 구분되면 "삭제된 글이 여기 있었다"는 사실이 새므로 404로 통일.
//    //   BLINDED : 관리자가 가린 글. 삭제와 달리 존재 자체는 감출 필요가 없어 사유를 알 수 있는 403으로 구분.
//    public PostDetailResponse getDetail(Long postId) {
//        // 응답에 nickname이 필요하므로 작성자까지 fetch join으로 함께 로딩한다.
//        Post post = postRepository.findWithUserByPostId(postId)
//                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
//
//        if (STATUS_DELETED.equals(post.getStatus())) {
//            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
//        }
//
//        if (STATUS_BLINDED.equals(post.getStatus())) {
//            throw new BusinessException(ErrorCode.POST_BLINDED);
//        }
//
//        return PostDetailResponse.from(post);
//    }



    // 요청 size를 검증한다. 0 이하는 의미가 없고, 상한을 넘으면 조용히 깎지 않고 400으로 알려준다.
    // 말없이 50으로 줄이면 클라이언트는 100건을 받은 줄 알고 51번째 글부터 건너뛴다.
    private int resolveSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return size;
    }

    // 요청으로 들어온 category를 조회 조건으로 변환한다.
    // 전체 조회(=조건 없음)면 null, 특정 카테고리면 그 값. 정의되지 않은 값이면 400으로 막는다.
    // 값을 검증하지 않고 그대로 넘기면 오타난 카테고리가 조용히 빈 목록을 반환해 프런트가 원인을 못 찾는다.
    private String resolveCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }

        String normalized = category.trim().toUpperCase(Locale.ROOT);
        if (CATEGORY_ALL.equals(normalized)) {
            return null;
        }

        if (!CATEGORIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    // 게시글 상세 조회: 단건을 조회해 상세 응답으로 반환한다. (클래스 기본 readOnly 트랜잭션)
    //
    // 노출 정책 — posts.status에 따라 갈린다.
    //   ACTIVE  : 정상 반환
    //   DELETED : 소프트 삭제된 글. 없는 글과 구분되면 "삭제된 글이 여기 있었다"는 사실이 새므로 404로 통일.
    //   BLINDED : 관리자가 가린 글. 삭제와 달리 존재 자체는 감출 필요가 없어 사유를 알 수 있는 403으로 구분.
    public PostDetailResponse getDetail(Long postId) {
        // 응답에 nickname이 필요하므로 작성자까지 fetch join으로 함께 로딩한다.
        Post post = postRepository.findWithUserByPostId(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (STATUS_DELETED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        if (STATUS_BLINDED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.POST_BLINDED);
        }

        return PostDetailResponse.from(post);
    }

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

    // 게시글 삭제: 작성자 본인만 상태를 DELETED로 바꾼다(소프트 삭제). (쓰기 트랜잭션)
    // userId는 컨트롤러에서 토큰(AuthUser)으로부터 넘어온 값이라 신뢰할 수 있다.
    @Transactional
    public void delete(Long userId, Long postId) {
        // 삭제할 게시글 조회, 없으면 예외
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        // 소유권 확인: 내 글이 아니면 삭제 거부(403). 권한(ROLE)과 별개로 서비스에서 막는다.
        if (!post.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 변경 감지로 status = DELETED 로 UPDATE 반영
        post.delete();
    }

}