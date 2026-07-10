package com.basecamp.backend.domain.campowner.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.basecamp.backend.domain.campowner.entity.ApplicationStatus;
import com.basecamp.backend.domain.campowner.entity.CampOwnerApplication;

public interface CampOwnerApplicationRepository extends JpaRepository<CampOwnerApplication, Long> {

	/**
	 * 심사 중인 신청이 이미 있는지 확인한다.
	 *
	 * <p>이 조회만으로는 동시 신청을 막지 못한다(조회와 INSERT 사이에 다른 요청이 끼어든다).
	 * 최종 방어선은 DB 의 {@code uq_coa_user_pending} 유니크 제약이다.</p>
	 */
	boolean existsByUserIdAndStatus(Long userId, ApplicationStatus status);

	/** 본인의 최신 신청 1건. 반려 후 재신청하면 여러 건이 쌓이므로 가장 최근 것을 본다. */
	Optional<CampOwnerApplication> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

	/** 상태별 신청 목록. 관리자 심사 화면에서 사용한다({@code idx_coa_status_created} 를 탄다). */
	Page<CampOwnerApplication> findByStatus(ApplicationStatus status, Pageable pageable);

}
