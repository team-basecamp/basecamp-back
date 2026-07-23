package com.basecamp.backend.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.admin.dto.response.ReportedPostResponse;
import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.entity.PostCategory;
import com.basecamp.backend.domain.post.entity.PostReport;
import com.basecamp.backend.domain.post.entity.PostStatus;
import com.basecamp.backend.domain.post.entity.ReportStatus;
import com.basecamp.backend.domain.post.repository.PostReportRepository;
import com.basecamp.backend.domain.post.repository.PostRepository;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 관리자의 신고 조회 및 블라인드 처리 단위 테스트.
 *
 * <p>핵심은 두 가지다. 조회는 status 를 생략하면 실제 조치 대상인 PENDING 으로 좁혀야 하고, 블라인드는 상태 전이 규칙(이미 블라인드 409 / 삭제된 글
 * 404)을 지키면서 성공 시에만 대기 신고를 정리해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminPostServiceTest {

  private static final Long POST_ID = 42L;

  @Mock private PostRepository postRepository;

  @Mock private PostReportRepository postReportRepository;

  private AdminPostService adminPostService;

  @BeforeEach
  void setUp() {
    adminPostService = new AdminPostService(postRepository, postReportRepository);
  }

  private Post activePost() {
    User author = User.register("작성자", "author@example.com", null, Provider.KAKAO);
    return new Post(author, PostCategory.GENERAL, "제목", "본문");
  }

  @Test
  @DisplayName("findReports_status미지정_PENDING으로좁혀조회한다")
  void findReports_status미지정_PENDING으로좁혀조회한다() {
    // given: 필터를 주지 않으면 관리자가 실제로 처리할 대기 신고만 보여야 한다.
    Pageable pageable = PageRequest.of(0, 20);
    given(postReportRepository.findByStatus(ReportStatus.PENDING, pageable))
        .willReturn(Page.empty(pageable));

    // when
    adminPostService.findReports(null, pageable);

    // then
    verify(postReportRepository).findByStatus(ReportStatus.PENDING, pageable);
  }

  @Test
  @DisplayName("findReports_지정된status로조회하고_신고를DTO로매핑한다")
  void findReports_지정된status로조회하고_DTO로매핑한다() {
    // given
    Pageable pageable = PageRequest.of(0, 20);
    User reporter = User.register("신고자", "reporter@example.com", null, Provider.KAKAO);
    PostReport report = new PostReport(activePost(), reporter, "INAPPROPRIATE", "부적절한 사진");
    given(postReportRepository.findByStatus(ReportStatus.ACCEPTED, pageable))
        .willReturn(new PageImpl<>(List.of(report), pageable, 1));

    // when
    Page<ReportedPostResponse> result =
        adminPostService.findReports(ReportStatus.ACCEPTED, pageable);

    // then
    assertThat(result.getContent()).hasSize(1);
    ReportedPostResponse dto = result.getContent().get(0);
    assertThat(dto.postTitle()).isEqualTo("제목");
    assertThat(dto.postStatus()).isEqualTo("ACTIVE");
    assertThat(dto.category()).isEqualTo("GENERAL");
    assertThat(dto.reason()).isEqualTo("INAPPROPRIATE");
    assertThat(dto.reporterNickname()).isEqualTo("신고자");
    verify(postReportRepository).findByStatus(ReportStatus.ACCEPTED, pageable);
  }

  @Test
  @DisplayName("blindPost_정상_상태를BLINDED로바꾸고_대기신고를ACCEPTED로정리한다")
  void blindPost_정상_상태를BLINDED로바꾸고_대기신고를정리한다() {
    // given
    Post post = activePost();
    given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

    // when
    adminPostService.blindPost(POST_ID, "음란성 게시물");

    // then
    assertThat(post.getStatus()).isEqualTo(PostStatus.BLINDED);
    assertThat(post.getBlindReason()).isEqualTo("음란성 게시물");
    verify(postReportRepository).acceptPendingReportsByPost(POST_ID);
  }

  @Test
  @DisplayName("blindPost_없는게시글_PO001을던지고_신고를건드리지않는다")
  void blindPost_없는게시글_PO001을던진다() {
    // given
    given(postRepository.findById(POST_ID)).willReturn(Optional.empty());

    // when & then
    assertBusinessException(
        () -> adminPostService.blindPost(POST_ID, "사유"), ErrorCode.POST_NOT_FOUND);
    verify(postReportRepository, never()).acceptPendingReportsByPost(anyLong());
  }

  @Test
  @DisplayName("blindPost_이미블라인드된글_PO004를던지고_신고를건드리지않는다")
  void blindPost_이미블라인드된글_PO004를던진다() {
    // given: 두 관리자가 같은 글을 동시에 블라인드하는 상황.
    Post post = activePost();
    post.blind("선처리");
    given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

    // when & then
    assertBusinessException(
        () -> adminPostService.blindPost(POST_ID, "재처리"), ErrorCode.POST_ALREADY_BLINDED);
    verify(postReportRepository, never()).acceptPendingReportsByPost(anyLong());
  }

  @Test
  @DisplayName("blindPost_삭제된글_PO001을던진다")
  void blindPost_삭제된글_PO001을던진다() {
    // given: 소프트 삭제된 글은 존재를 드러내지 않도록 없는 글과 같게 404로 통일한다.
    Post post = activePost();
    post.delete();
    given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

    // when & then
    assertBusinessException(
        () -> adminPostService.blindPost(POST_ID, "사유"), ErrorCode.POST_NOT_FOUND);
    verify(postReportRepository, never()).acceptPendingReportsByPost(anyLong());
  }

  private void assertBusinessException(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(expected);
  }
}
