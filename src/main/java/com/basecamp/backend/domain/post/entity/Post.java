package com.basecamp.backend.domain.post.entity;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

  // 게시판 카테고리 (GENERAL / CAMP_MATE / RESERVATION_TRANSFER).
  // VARCHAR 컬럼에 enum 이름 문자열로 저장한다(@Enumerated(STRING)).
  @Enumerated(EnumType.STRING)
  @Column(length = 30, nullable = false)
  private PostCategory category;

  // 게시글 제목
  @Column(length = 200, nullable = false)
  private String title;

  // 게시글 본문 (TEXT)
  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  // 조회수 (생성 시 0으로 시작)
  @Column(name = "view_count", nullable = false)
  private Integer viewCount;

  // 게시글 상태 (ACTIVE / BLINDED / DELETED).
  // VARCHAR 컬럼에 enum 이름 문자열로 저장한다(@Enumerated(STRING)).
  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private PostStatus status;

  // 관리자 블라인드 처리 사유 (없으면 null)
  @Column(name = "blind_reason", length = 200)
  private String blindReason;

  // 게시글 첨부 이미지 (1 게시글 : N 이미지). V3의 공용 저장소(images)와 중간 테이블(post_images)로 연결한다.
  // post_images는 순수 LIST 조인이라 @OrderColumn(sort_order)로 첨부 순서를 그대로 보존한다.
  // cascade=PERSIST: 게시글 저장 시 새 Image 행과 연결(post_images)이 함께 저장된다. (created_at은 DB DEFAULT)
  @ManyToMany(cascade = CascadeType.PERSIST)
  @JoinTable(
      name = "post_images",
      joinColumns = @JoinColumn(name = "post_id"),
      inverseJoinColumns = @JoinColumn(name = "image_id"))
  @OrderColumn(name = "sort_order")
  private List<Image> images = new ArrayList<>();

  // 작성 일시
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  // 수정 일시 (수정 전에는 null)
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  // 동시에 들어온 수정/삭제 요청이 서로의 변경(특히 소프트 삭제)을 덮어쓰는 것을 막기 위한 낙관적 락.
  // 버전이 어긋난 두 번째 UPDATE 는 0건이 되어 ObjectOptimisticLockingFailureException 으로 실패하고,
  // GlobalExceptionHandler 가 CONCURRENT_MODIFICATION(C005)로 변환한다.
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  // 게시글 생성자. 작성자·카테고리·제목·본문을 받고 나머지 초기값(조회수/상태/작성시각)은 여기서 채운다.
  public Post(User user, PostCategory category, String title, String content) {
    // 작성자: 인증된 사용자(JWT principal)로 조회한 회원 엔티티
    this.user = user;
    this.category = category;
    this.title = title;
    this.content = content;
    // DB DEFAULT가 있어도 JPA가 NULL로 밀어넣으면 적용되지 않아 자바단에서 초기값을 채운다.
    this.viewCount = 0;
    this.status = PostStatus.ACTIVE;
    this.createdAt = LocalDateTime.now();
  }

  // 게시글에 이미지를 첨부한다. 저장소에 올린 뒤 만든 Image들을 순서대로 붙인다(전달 순서가 곧 노출 순서).
  // 비어 있으면 아무것도 하지 않는다.
  public void attachImages(List<Image> images) {
    if (images == null || images.isEmpty()) {
      return;
    }
    this.images.addAll(images);
  }

  // 첨부 이미지를 전달받은 목록으로 통째로 교체하고, 이번 교체로 떨어져 나간 이미지들을 돌려준다.
  // 수정은 PostMapping 전체 교체라 "남길 기존 것 + 새로 올린 것"의 최종 목록을 그대로 받는다.
  // 반환된 이미지는 이 게시글에서 완전히 빠진 것들이라, 호출측이 images 행과 디스크 파일을 정리하는 근거로 쓴다.
  // 비교는 Image 인스턴스 동일성 기준이다. 남기는 이미지는 이미 이 컬렉션에 로딩된 바로 그 객체를 다시 넘겨야 한다.
  public List<Image> replaceImages(List<Image> newImages) {
    List<Image> detached = new ArrayList<>(this.images);
    detached.removeAll(newImages);

    this.images.clear();
    this.images.addAll(newImages);
    this.updatedAt = LocalDateTime.now();

    return detached;
  }

  // 게시글 수정. 변경 감지(dirty checking)로 트랜잭션 커밋 시점에 UPDATE 되도록 필드 값만 바꾼다.
  public void update(PostCategory category, String title, String content) {
    this.category = category;
    this.title = title;
    this.content = content;
    this.updatedAt = LocalDateTime.now();
  }

  // 게시글 삭제(소프트 삭제). 실제 행을 지우지 않고 상태만 DELETED로 바꾼다.
  public void delete() {
    this.status = PostStatus.DELETED;
    this.updatedAt = LocalDateTime.now();
  }

  // 관리자 블라인드 처리. 상태를 BLINDED로 바꾸고 사유를 남긴다.
  // 상태 전이 규칙은 여기서 강제한다(Reservation.approve/reject 와 같은 방식):
  //   이미 BLINDED  : 중복 처리이므로 409(POST_ALREADY_BLINDED)
  //   DELETED       : 삭제된 글은 블라인드 대상이 아니고, 존재를 드러내지 않도록 404(POST_NOT_FOUND)
  public void blind(String reason) {
    if (this.status == PostStatus.BLINDED) {
      throw new BusinessException(ErrorCode.POST_ALREADY_BLINDED);
    }
    if (this.status == PostStatus.DELETED) {
      throw new BusinessException(ErrorCode.POST_NOT_FOUND);
    }
    this.status = PostStatus.BLINDED;
    this.blindReason = reason;
    this.updatedAt = LocalDateTime.now();
  }
}
