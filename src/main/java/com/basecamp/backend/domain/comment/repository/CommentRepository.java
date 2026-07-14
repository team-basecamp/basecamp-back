package com.basecamp.backend.domain.comment.repository;

import com.basecamp.backend.domain.comment.dto.PostCommentCount;
import com.basecamp.backend.domain.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 게시글 목록의 댓글 수를 한 번에 집계한다.
    // 게시글마다 count 쿼리를 따로 날리면 페이지 크기만큼 N+1이 되므로,
    // 목록에 실린 post_id 전체를 IN으로 묶어 GROUP BY 한 번으로 끝낸다. (목록 1 + 집계 1 = 2쿼리)
    //
    // c.post.postId는 comments.post_id FK 컬럼을 그대로 읽으므로 posts 테이블 조인이 붙지 않는다.
    // 댓글이 하나도 없는 게시글은 결과에 아예 나오지 않는다 → 호출측에서 0으로 채운다.
    // (comments에는 soft-delete 컬럼이 없어 전체 COUNT가 곧 노출용 댓글 수와 같다.)
    @Query("""
            select new com.basecamp.backend.domain.comment.dto.PostCommentCount(c.post.postId, count(c))
            from Comment c
            where c.post.postId in :postIds
            group by c.post.postId
            """)
    List<PostCommentCount> countByPostIds(@Param("postIds") List<Long> postIds);
}
