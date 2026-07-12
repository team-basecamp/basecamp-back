package com.basecamp.backend.domain.post.entity;

import com.basecamp.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 게시글 엔티티. posts 테이블과 매핑되며, 게시판 글 한 건을 표현한다.
@Entity
@Getter
// 기본 생성자는 JPA가 요구하지만 외부에서 new Post() 남용을 막기 위해 PROTECTED로 제한
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "posts")
public class Post {

    // 게시글 PK (AUTO_INCREMENT)
    @Id
    @Column(name = "post_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postId;

    // 작성자 회원 (posts.user_id FK → users).
    // 목록/상세에서 매번 회원을 조인하지 않도록 LAZY, 닉네임 등 회원 정보 접근 시에만 프록시 초기화.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 게시판 카테고리 (GENERAL / CAMP_MATE / RESERVATION_TRANSFER)
    @Column(length = 30, nullable = false)
    private String category;

    // 게시글 제목
    @Column(length = 200, nullable = false)
    private String title;

    // 게시글 본문 (TEXT)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // 조회수 (생성 시 0으로 시작)
    @Column(name = "view_count", nullable = false)
    private Integer viewCount;

    // 게시글 상태 (ACTIVE / BLINDED / DELETED)
    @Column(length = 20, nullable = false)
    private String status;

    // 관리자 블라인드 처리 사유 (없으면 null)
    @Column(name = "blind_reason", length = 200)
    private String blindReason;

    // 작성 일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 수정 일시 (수정 전에는 null)
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 게시글 생성자. 작성자·카테고리·제목·본문을 받고 나머지 초기값(조회수/상태/작성시각)은 여기서 채운다.
    public Post(User user, String category, String title, String content) {
        // 작성자: 인증된 사용자(JWT principal)로 조회한 회원 엔티티
        this.user = user;
        this.category = category;
        this.title = title;
        this.content = content;
        // DB DEFAULT가 있어도 JPA가 NULL로 밀어넣으면 적용되지 않아 자바단에서 초기값을 채운다.
        this.viewCount = 0;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
    }

    // 게시글 수정. 변경 감지(dirty checking)로 트랜잭션 커밋 시점에 UPDATE 되도록 필드 값만 바꾼다.
    public void update(String category, String title, String content) {
        this.category = category;
        this.title = title;
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    // 게시글 삭제(소프트 삭제). 실제 행을 지우지 않고 상태만 DELETED로 바꾼다.
    public void delete() {
        this.status = "DELETED";
        this.updatedAt = LocalDateTime.now();
    }
}

