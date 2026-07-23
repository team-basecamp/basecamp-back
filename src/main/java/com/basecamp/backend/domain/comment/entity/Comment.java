package com.basecamp.backend.domain.comment.entity;

import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 댓글 엔티티. comments 테이블과 매핑되며, 게시글에 달린 댓글 한 건을 표현한다.
// 계층형/대댓글/멘션이 없는 flat 구조라 부모 댓글(parent) 참조를 두지 않는다.
@Entity
@Getter
// 기본 생성자는 JPA가 요구하지만 외부에서 new Comment() 남용을 막기 위해 PROTECTED로 제한
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "comments")
public class Comment {

  // 댓글 PK (AUTO_INCREMENT)
  @Id
  @Column(name = "comment_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long commentId;

  // 댓글이 달린 게시글 (comments.post_id FK → posts).
  // 목록/작성에서 매번 게시글을 조인하지 않도록 LAZY, 게시글 정보 접근 시에만 프록시 초기화.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false)
  private Post post;

  // 작성자 회원 (comments.user_id FK → users).
  // 응답에 nickname 등 회원 정보 접근 시에만 프록시 초기화하도록 LAZY.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  // 댓글 본문 (TEXT)
  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  // 댓글 노출 상태 (comments.status). 게시글 status와 동일한 소프트 삭제 정책.
  // 실제 행은 지우지 않고 이 값만 바꾸며, 목록에서는 상태에 맞는 안내 문구로 대체 노출한다.
  // 이름 문자열('ACTIVE'/'BLINDED'/'DELETED')로 저장하도록 EnumType.STRING을 쓴다.
  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private CommentStatus status;

  // 작성 일시
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  // 수정 일시 (수정 전에는 null)
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  // 댓글 생성자. 대상 게시글·작성자·본문을 받고 작성 시각은 여기서 채운다.
  public Comment(Post post, User user, String content) {
    // 대상 게시글: 댓글이 달리는 게시글 엔티티
    this.post = post;
    // 작성자: 인증된 사용자(JWT principal)로 조회한 회원 엔티티
    this.user = user;
    this.content = content;
    // DB DEFAULT가 있어도 JPA가 NULL로 밀어넣으면 적용되지 않아 자바단에서 초기값을 채운다.
    this.status = CommentStatus.ACTIVE;
    this.createdAt = LocalDateTime.now();
  }

  // 댓글 본문 수정. 내용만 갈아끼우고 수정 시각을 현재로 채운다.
  // 관리 상태(영속 컨텍스트) 엔티티에서 호출하면 트랜잭션 커밋 시 변경 감지로 UPDATE가 나간다.
  public void updateContent(String content) {
    this.content = content;
    this.updatedAt = LocalDateTime.now();
  }

  // 아직 노출 중인 정상 댓글인지 여부. 이미 삭제/블라인드된 댓글의 재삭제·수정을 막는 데 쓴다.
  public boolean isActive() {
    return this.status.isActive();
  }

  // 작성자 본인 삭제(소프트 삭제). 실제 행을 지우지 않고 상태만 DELETED로 바꾼다.
  public void deleteByOwner() {
    this.status = CommentStatus.DELETED;
    this.updatedAt = LocalDateTime.now();
  }

  // 관리자 삭제(소프트 삭제). 존재 자체는 남기고 상태만 BLINDED로 바꿔 안내 문구로 가린다.
  public void blindByAdmin() {
    this.status = CommentStatus.BLINDED;
    this.updatedAt = LocalDateTime.now();
  }
}
