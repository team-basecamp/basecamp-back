package com.basecamp.backend.domain.post.repository;

import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.entity.PostCategory;
import com.basecamp.backend.domain.post.entity.PostStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // 상세 조회용 단건 조회.
    // PostDetailResponse가 작성자 nickname을 요구하는데 Post.user는 LAZY라
    // 기본 findById로 가져오면 DTO 변환 시점에 회원 조회 쿼리가 한 번 더 나간다.
    // fetch join으로 작성자까지 한 번에 로딩해 추가 쿼리를 없앤다.
    @Query("select p from Post p join fetch p.user where p.postId = :postId")
    Optional<Post> findWithUserByPostId(@Param("postId") Long postId);


    // -------w--------------------------------------------------------------
    // 게시글 목록 조회 (커서 페이징)
    //
    // 정렬 키는 (created_at DESC, post_id DESC) 하나로 고정하고, 커서도 이 두 값을 그대로 담는다.
    // OFFSET을 쓰지 않으므로 조회 중 글이 새로 등록/삭제돼도 목록이 밀리지 않는다.
    // (OFFSET은 "앞에서 N건 건너뛰기"라 앞쪽이 바뀌면 경계에서 중복·누락이 난다.)
    //
    // 페이징 방식이 Pageable이 아닌 Limit인 이유:
    //   Pageable은 offset을 함께 실어 나르는 타입이라, 커서 쿼리에 넘기면 항상 offset=0을 넣는
    //   무의미한 관례가 생기고 읽는 사람이 offset 페이징으로 오해한다. Limit은 개수만 뜻한다.
    //
    // hasNext 판정을 위해 서비스가 limit + 1건을 요청한다. COUNT 쿼리는 없다.
    //
    // 커서 조건을 (created_at, post_id) < (:createdAt, :postId) 튜플 비교로 쓰지 않은 이유:
    //   JPQL이 행 생성자 비교를 지원하지 않는다. 아래 OR 형태는 결국 created_at <= :createdAt
    //   범위로 좁혀지므로 인덱스 레인지 스캔을 그대로 타고, 경계의 동일 시각 몇 건만 필터로 걸러진다.
    //
    // 인덱스: idx_posts_status   (status, created_at DESC, post_id DESC)          — 전체 조회
    //        idx_posts_category (category, status, created_at DESC, post_id DESC) — 카테고리 조회
    //
    // category 유무 / cursor 유무의 4가지 경우를 한 쿼리에 (:category is null or ...)로 합치지 않고
    // 메서드를 4개로 나눈 이유: MySQL은 그런 상수 OR 조건을 인덱스 조건으로 밀어넣지 못해
    // 카테고리 인덱스를 버리고 풀스캔으로 떨어진다.
    // ---------------------------------------------------------------------

    // 전체 카테고리 · 첫 페이지 (커서 없음)
    @EntityGraph(attributePaths = "user")
    @Query("""
            select p from Post p
            where p.status = :status
            order by p.createdAt desc, p.postId desc
            """)
    List<Post> findFirstPage(
            @Param("status") PostStatus status,
            Limit limit);

    // 전체 카테고리 · 다음 페이지 (커서 이후)
    @EntityGraph(attributePaths = "user")
    @Query("""
            select p from Post p
            where p.status = :status
              and (p.createdAt < :createdAt
                   or (p.createdAt = :createdAt and p.postId < :postId))
            order by p.createdAt desc, p.postId desc
            """)
    List<Post> findNextPage(
            @Param("status") PostStatus status,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("postId") Long postId,
            Limit limit);

    // 특정 카테고리 · 첫 페이지 (커서 없음)
    @EntityGraph(attributePaths = "user")
    @Query("""
            select p from Post p
            where p.status = :status
              and p.category = :category
            order by p.createdAt desc, p.postId desc
            """)
    List<Post> findFirstPageByCategory(
            @Param("status") PostStatus status,
            @Param("category") PostCategory category,
            Limit limit);

    // 특정 카테고리 · 다음 페이지 (커서 이후)
    @EntityGraph(attributePaths = "user")
    @Query("""
            select p from Post p
            where p.status = :status
              and p.category = :category
              and (p.createdAt < :createdAt
                   or (p.createdAt = :createdAt and p.postId < :postId))
            order by p.createdAt desc, p.postId desc
            """)
    List<Post> findNextPageByCategory(
            @Param("status") PostStatus status,
            @Param("category") PostCategory category,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("postId") Long postId,
            Limit limit);


    // ---------------------------------------------------------------------
    // 마이페이지 "내가 쓴 게시글" 목록 조회 (커서 페이징)
    //
    // 공용 목록과 정렬 키 · 커서 조건은 완전히 같고, 카테고리 조건 대신 작성자(user_id) 조건이 붙는다.
    // status = ACTIVE 만 노출한다 — 소프트 삭제(DELETED)한 글이 새로고침에 되살아나면 안 되고,
    // 관리자 블라인드(BLINDED)된 글도 공용 목록과 같은 기준으로 가린다.
    //
    // 인덱스: idx_posts_user (user_id, status, created_at DESC, post_id DESC) — V21에서 재정의.
    //   category 조회와 같은 이유로 (user_id is null or ...) 합침 없이 커서 유무에 따라 메서드를 나눈다.
    // ---------------------------------------------------------------------

    // 내 글 · 첫 페이지 (커서 없음)
    // 응답(MyPostResponse)이 작성자 nickname을 쓰지 않으므로 공용 목록과 달리 user를 fetch 하지 않는다.
    // where 조건의 p.user.id는 posts.user_id FK 컬럼을 그대로 읽어 users 조인이 붙지 않는다.
    @Query("""
            select p from Post p
            where p.status = :status
              and p.user.id = :userId
            order by p.createdAt desc, p.postId desc
            """)
    List<Post> findFirstPageByUser(
            @Param("status") PostStatus status,
            @Param("userId") Long userId,
            Limit limit);

    // 내 글 · 다음 페이지 (커서 이후)
    @Query("""
            select p from Post p
            where p.status = :status
              and p.user.id = :userId
              and (p.createdAt < :createdAt
                   or (p.createdAt = :createdAt and p.postId < :postId))
            order by p.createdAt desc, p.postId desc
            """)
    List<Post> findNextPageByUser(
            @Param("status") PostStatus status,
            @Param("userId") Long userId,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("postId") Long postId,
            Limit limit);
}
