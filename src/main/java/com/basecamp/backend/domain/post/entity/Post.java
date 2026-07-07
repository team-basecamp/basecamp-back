package com.basecamp.backend.domain.post.entity;

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
// 생성하는 시간 자바단이냐 디비단이냐?
public class Post {

    @Id
    @Column(name = "post_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 30, nullable = false)
    private String category;

    @Column(length = 200, nullable = false)
    private String title;

    // 얘도 정체가 뭐냐  TEXT 맞냐
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "view_count")
    private Integer viewCount;

    @Column(length = 20)
    private String status;

    @Column(name = "blind_reason", length = 200)
    private String blindReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 이건 머냐 왜 만들었냐
    public Post(String title, String content) {
        this.title = title;
        this.content = content;
    }
}

