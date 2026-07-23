package com.basecamp.backend.domain.comment.repository;

import com.basecamp.backend.domain.comment.dto.PostCommentCount;
import com.basecamp.backend.domain.comment.entity.Comment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

  // 게시글 목록의 댓글 수를 한 번에 집계한다.
  // 게시글마다 count 쿼리를 따로 날리면 페이지 크기만큼 N+1이 되므로,
  // 목록에 실린 post_id 전체를 IN으로 묶어 GROUP BY 한 번으로 끝낸다. (목록 1 + 집계 1 = 2쿼리)
  //
  // c.post.postId는 comments.post_id FK 컬럼을 그대로 읽으므로 posts 테이블 조인이 붙지 않는다.
  // 댓글이 하나도 없는 게시글은 결과에 아예 나오지 않는다 → 호출측에서 0으로 채운다.
  // (comments에는 soft-delete 컬럼이 없어 전체 COUNT가 곧 노출용 댓글 수와 같다.)
  @Query(
      """
            select new com.basecamp.backend.domain.comment.dto.PostCommentCount(c.post.postId, count(c))
            from Comment c
            where c.post.postId in :postIds
            group by c.post.postId
            """)
  List<PostCommentCount> countByPostIds(@Param("postIds") List<Long> postIds);

  // 한 게시글의 댓글 목록을 작성 순(오래된 → 최신)으로 조회한다.
  // 응답에 작성자 nickname·프로필 사진을 담아야 하는데, 댓글마다 user·image를 따로 로딩하면 N+1이 된다.
  //   - join fetch c.user      : 작성자를 함께 로딩 (user_id는 NOT NULL이라 inner join)
  //   - left join fetch u.profileImage : 프로필 이미지는 없을 수 있어(NULL 허용) left join으로 결측 회원도 누락 없이 담는다.
  // 결과적으로 댓글 목록 + 작성자 + 프로필 이미지를 쿼리 한 번으로 가져온다.
  @Query(
      """
            select c from Comment c
            join fetch c.user u
            left join fetch u.profileImage
            where c.post.postId = :postId
            order by c.createdAt asc, c.commentId asc
            """)
  List<Comment> findByPostIdWithUser(@Param("postId") Long postId);

  // 단건 댓글을 작성자·프로필 사진·게시글과 함께 조회한다. (수정 시 노출 정책 검증과 응답 변환을 추가 쿼리 없이 처리)
  //   - join fetch c.user      : 작성자를 함께 로딩 (user_id는 NOT NULL이라 inner join)
  //   - left join fetch u.profileImage : 프로필 이미지는 없을 수 있어(NULL 허용) left join으로 담는다.
  //   - join fetch c.post      : 게시글 노출 정책(status) 검증에 쓰므로 함께 로딩 (post_id는 NOT NULL이라 inner join)
  @Query(
      """
            select c from Comment c
            join fetch c.user u
            left join fetch u.profileImage
            join fetch c.post
            where c.commentId = :commentId
            """)
  Optional<Comment> findByIdWithUser(@Param("commentId") Long commentId);
}
