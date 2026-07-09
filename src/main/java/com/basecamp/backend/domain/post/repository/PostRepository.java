package com.basecamp.backend.domain.post.repository;

import com.basecamp.backend.domain.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // 상세 조회용 단건 조회.
    // PostDetailResponse가 작성자 nickname을 요구하는데 Post.user는 LAZY라
    // 기본 findById로 가져오면 DTO 변환 시점에 회원 조회 쿼리가 한 번 더 나간다.
    // fetch join으로 작성자까지 한 번에 로딩해 추가 쿼리를 없앤다.
    @Query("select p from Post p join fetch p.user where p.postId = :postId")
    Optional<Post> findWithUserByPostId(@Param("postId") Long postId);
}
