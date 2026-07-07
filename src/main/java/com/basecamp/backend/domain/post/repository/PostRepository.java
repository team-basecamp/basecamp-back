package com.basecamp.backend.domain.post.repository;

import com.basecamp.backend.domain.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


// 여기다가는 메서드 이름 적기
// ㄴ있는 것도 있고 없는 것도 있고 -> 차이가 뭘까?
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
// 이미 완성된 메서들은 추가 안해도 된다.



}
