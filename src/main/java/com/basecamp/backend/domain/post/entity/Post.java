package com.basecamp.backend.domain.post.entity;

import com.basecamp.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
//import jakarta.persistence.Entity;
//import jakarta.persistence.Table;
//import jakarta.persistence.Column;
//import jakarta.persistence.Id;
//import jakarta.persistence.GeneratedValue;
//import jakarta.persistence.GenerationType;

import java.time.LocalDateTime;

// 클래스명과 테이블명이 달라도 되나요?
//네, 완전히 달라도 됩니다.
@Entity

@Getter
// Entity를 생성할 때 기본 생성자(매개변수가 없는 생성자)가 반드시 필요
// 외부에서 마음대로 new Post()를 호출하는 것은 막기 위해서입니다.
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 왜?

@Table(name = "posts")
// 생성하는 시간 자바단이냐 디비단이냐? ?
public class Post {
// nullable 세팅해야하나?
    @Id
    @Column(name = "post_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postId;

    // posts.user_id (FK) → users. 목록/상세에서 매번 회원을 조인하지 않도록 LAZY.
    // 작성자 닉네임 등 회원 정보가 필요할 때만 프록시가 초기화된다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 얘 디폴트 있으면 좋을 듯
    @Column(length = 30, nullable = false)
    private String category;

    @Column(length = 200, nullable = false)
    private String title;

    // 얘도 정체가 뭐냐  TEXT 맞냐
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "blind_reason", length = 200)
    private String blindReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 이건 머냐 왜 만들었냐
    // userId, category 더 필요한가? -> 예시 자료에는 없네?
    // post 말고 void로 create로 하는게 맞을 수도 변수명 물론 생성자니까 관습
    // 적인거 뭐 있는디
    public Post(User user, String category, String title, String content) {
        // 작성자: 인증된 사용자(JWT principal)로 조회한 회원 엔티티
        this.user = user;
        this.category = category;
        this.title = title;
        this.content = content;
        // DB에 DEFAULT가 있어도 JPA가 NULL로 밀어넣으면 적용되지 않아 자바단에서 채운다.
        this.viewCount = 0;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
    }

    // 게시글 수정: 변경 감지(dirty checking)로 UPDATE 되도록 필드 값만 바꾼다.
    // 얜 왜 반환값이 없고 Post는 생성자인디 왜 반환값이 있ㄴ느 함수 인것인가?
    public void update(String category, String title, String content) {
        this.category = category;
        this.title = title;
        this.content = content;
        // 너 맞냐 now 있는거 ? 디비단 or 자바단?
        this.updatedAt = LocalDateTime.now();
    }

    // 작성자 id만 필요할 때 쓰는 편의 메서드.
    // LAZY 프록시에서 getId()는 프록시 초기화(추가 쿼리) 없이 식별자를 돌려준다.
    public Long getUserId() {
        return user == null ? null : user.getId();
    }
}

