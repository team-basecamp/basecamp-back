package com.basecamp.backend.domain.comment.entity;

// 댓글 노출 상태. comments.status 컬럼(VARCHAR)에 이름 문자열로 저장된다. (@Enumerated(EnumType.STRING))
// 게시글 status와 동일한 소프트 삭제 정책: 실제 행은 지우지 않고 상태만 바꾸며,
// 삭제된 댓글은 목록에서 실제 본문 대신 상태별 안내 문구(notice)로 대체 노출한다.
public enum CommentStatus {

  // 정상 노출 상태. 안내 문구가 없어(null) 실제 본문을 그대로 보여준다.
  ACTIVE(null),

  // 관리자가 삭제(가림)한 상태.
  BLINDED("관리자에 의해 삭제된 글입니다."),

  // 작성자 본인이 삭제한 상태.
  DELETED("사용자에 의해 삭제된 글입니다.");

  // 삭제된 댓글에서 실제 본문 대신 노출할 안내 문구. ACTIVE는 없음(null).
  private final String notice;

  CommentStatus(String notice) {
    this.notice = notice;
  }

  // 상태별 안내 문구. ACTIVE면 null → 실제 본문을 그대로 노출해야 한다는 의미.
  public String getNotice() {
    return notice;
  }

  // 아직 정상 노출 중인 댓글인지 여부. 이미 삭제/블라인드된 댓글의 재삭제·수정을 막는 데 쓴다.
  public boolean isActive() {
    return this == ACTIVE;
  }
}
