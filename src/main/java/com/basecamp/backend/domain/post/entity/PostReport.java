package com.basecamp.backend.domain.post.entity;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 게시글 신고 엔티티. post_reports 테이블(V1)과 매핑되며, 신고 한 건을 표현한다.
@Entity
@Getter
// 기본 생성자는 JPA가 요구하지만 외부에서 new PostReport() 남용을 막기 위해 PROTECTED로 제한
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_reports")
public class PostReport {

  // 신고 PK (AUTO_INCREMENT)
  @Id
  @Column(name = "report_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long reportId;

  // 신고 대상 게시글 (post_reports.post_id FK → posts).
  // 저장 시 FK 값만 있으면 되고 신고 처리 흐름에서 게시글 필드를 읽지 않으므로 LAZY.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false)
  private Post post;

  // 신고한 회원 (post_reports.reporter_id FK → users).
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reporter_id", nullable = false)
  private User reporter;

  // 신고 사유 (SPAM / INAPPROPRIATE / ILLEGAL / ETC)
  @Column(length = 50, nullable = false)
  private String reason;

  // 신고 상세 내용 (TEXT, 선택 입력)
  @Column(columnDefinition = "TEXT")
  private String description;

  // 신고 처리 상태 (PENDING / ACCEPTED / REJECTED). 접수 시 PENDING으로 시작한다.
  // VARCHAR 컬럼에 enum 이름 문자열로 저장한다(@Enumerated(STRING)).
  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private ReportStatus status;

  // 신고 접수 일시
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  // 신고 생성자. 대상 게시글·신고자·사유·상세를 받고 나머지 초기값(상태/접수시각)은 여기서 채운다.
  public PostReport(Post post, User reporter, String reason, String description) {
    this.post = post;
    this.reporter = reporter;
    this.reason = reason;
    this.description = description;
    // DB DEFAULT가 있어도 JPA가 NULL로 밀어넣으면 적용되지 않아 자바단에서 초기값을 채운다.
    this.status = ReportStatus.PENDING;
    this.createdAt = LocalDateTime.now();
  }

  // 관리자 신고 반려. 블라인드 등 조치 없이 신고를 기각한다(PENDING → REJECTED).
  // 상태 전이 규칙은 여기서 강제한다(Post.blind 와 같은 방식):
  //   PENDING 이 아니면(이미 ACCEPTED/REJECTED) 중복 처리이므로 409(REPORT_ALREADY_PROCESSED).
  // 변경 감지(dirty checking)로 트랜잭션 커밋 시점에 UPDATE 된다.
  public void reject() {
    if (this.status != ReportStatus.PENDING) {
      throw new BusinessException(ErrorCode.REPORT_ALREADY_PROCESSED);
    }
    this.status = ReportStatus.REJECTED;
  }
}
