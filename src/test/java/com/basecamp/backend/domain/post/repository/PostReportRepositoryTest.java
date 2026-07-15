package com.basecamp.backend.domain.post.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.basecamp.backend.common.config.JpaConfig;
import com.basecamp.backend.domain.camp.config.CampNamingStrategyConfig;
import com.basecamp.backend.domain.post.entity.Post;
import com.basecamp.backend.domain.post.entity.PostCategory;
import com.basecamp.backend.domain.post.entity.PostReport;
import com.basecamp.backend.domain.post.entity.ReportStatus;
import com.basecamp.backend.domain.user.entity.Provider;
import com.basecamp.backend.domain.user.entity.User;

/**
 * {@link PostReportRepository} 의 관리자 조회/정리 쿼리를 실제 MySQL 로 검증한다.
 *
 * <ul>
 *   <li>{@code findByStatus} — 처리 상태로 거른 페이지 조회 + 대상 게시글/신고자 연관 로딩</li>
 *   <li>{@code acceptPendingReportsByPost} — 특정 글의 PENDING 신고만 골라 ACCEPTED 로 바꾸는 벌크 UPDATE(JPQL)</li>
 * </ul>
 *
 * <p>목/스텁으로는 JPQL 파싱과 벌크 UPDATE 의 대상 필터가 검증되지 않아 리포지토리 테스트로 확인한다.
 * 임베디드 DB 대신 로컬 MySQL 을 그대로 쓴다({@code UserRepositoryTest} 와 같은 이유·설정).</p>
 */
@DataJpaTest
@Import({JpaConfig.class, CampNamingStrategyConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostReportRepositoryTest {

	@Autowired
	private PostReportRepository postReportRepository;

	@Autowired
	private TestEntityManager em;

	private User persistUser(String nickname, String email) {
		return em.persistFlushFind(User.register(nickname, email, null, Provider.KAKAO));
	}

	private Post persistPost(User author) {
		return em.persistFlushFind(new Post(author, PostCategory.GENERAL, "제목", "본문"));
	}

	/** 접수 직후(PENDING) 신고를 저장한다. */
	private PostReport persistPendingReport(Post post, User reporter) {
		return em.persistFlushFind(new PostReport(post, reporter, "INAPPROPRIATE", "부적절"));
	}

	/** 이미 처리(ACCEPTED)된 과거 신고를 저장한다. V18 파생 유니크는 PENDING 이 아니면 NULL 이라 충돌하지 않는다. */
	private PostReport persistAcceptedReport(Post post, User reporter) {
		PostReport report = new PostReport(post, reporter, "SPAM", null);
		ReflectionTestUtils.setField(report, "status", ReportStatus.ACCEPTED);
		return em.persistFlushFind(report);
	}

	@Test
	@DisplayName("findByStatus_PENDING만조회하고_대상글과신고자를함께로딩한다")
	void findByStatus_지정한상태만조회한다() {
		// given: 같은 글에 PENDING 2건(신고자 2명), 다른 글에 이미 처리된 ACCEPTED 1건.
		User author = persistUser("작성자", "author@example.com");
		User reporterA = persistUser("신고자A", "reporterA@example.com");
		User reporterB = persistUser("신고자B", "reporterB@example.com");
		Post reported = persistPost(author);
		Post other = persistPost(author);
		persistPendingReport(reported, reporterA);
		persistPendingReport(reported, reporterB);
		persistAcceptedReport(other, reporterA);
		em.clear();

		// when
		Page<PostReport> pending = postReportRepository.findByStatus(ReportStatus.PENDING, PageRequest.of(0, 20));

		// then: PENDING 2건만, ACCEPTED 는 빠진다. 연관 로딩이 되어 글 제목·신고자 닉네임에 접근할 수 있다.
		assertThat(pending.getTotalElements()).isEqualTo(2);
		assertThat(pending.getContent())
				.allSatisfy(r -> {
					assertThat(r.getStatus()).isEqualTo(ReportStatus.PENDING);
					assertThat(r.getPost().getTitle()).isEqualTo("제목");
					assertThat(r.getReporter().getNickname()).startsWith("신고자");
				});

		assertThat(postReportRepository.findByStatus(ReportStatus.ACCEPTED, PageRequest.of(0, 20)).getTotalElements())
				.isEqualTo(1);
	}

	@Test
	@DisplayName("acceptPendingReportsByPost_대상글의PENDING신고만ACCEPTED로바꾼다")
	void acceptPendingReportsByPost_대상글의PENDING만바꾼다() {
		// given: reported 글에 PENDING 2건, untouched 글에 PENDING 1건.
		User author = persistUser("작성자", "author@example.com");
		User reporterA = persistUser("신고자A", "reporterA@example.com");
		User reporterB = persistUser("신고자B", "reporterB@example.com");
		Post reported = persistPost(author);
		Post untouched = persistPost(author);
		persistPendingReport(reported, reporterA);
		persistPendingReport(reported, reporterB);
		persistPendingReport(untouched, reporterA);

		// when: reported 글의 대기 신고만 정리한다. (clearAutomatically=true 라 영속성 컨텍스트도 비워진다)
		int updated = postReportRepository.acceptPendingReportsByPost(reported.getPostId());

		// then: reported 의 2건만 바뀐다.
		assertThat(updated).isEqualTo(2);
		assertThat(postReportRepository.findByStatus(ReportStatus.PENDING, PageRequest.of(0, 20)).getTotalElements())
				.isEqualTo(1); // untouched 글의 1건만 남는다
		assertThat(postReportRepository.findByStatus(ReportStatus.ACCEPTED, PageRequest.of(0, 20)).getTotalElements())
				.isEqualTo(2);
	}
}
