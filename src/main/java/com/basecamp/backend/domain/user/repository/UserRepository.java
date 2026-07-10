package com.basecamp.backend.domain.user.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.entity.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {

	/**
	 * 활성 회원을 이메일로 조회한다.
	 *
	 * <p>V3에서 {@code provider_id} 가 제거되어, 소셜 회원 식별은 {@code email} 로 한다.</p>
	 *
	 * <p>탈퇴 회원(soft delete)의 행은 email 을 그대로 들고 남지만 조회 대상이 아니다. 그래야 같은 소셜 계정으로
	 * 다시 로그인했을 때 옛 행을 되살리지 않고 신규 회원으로 가입시킬 수 있다. DB 레벨에서도 유니크 제약이
	 * 활성 회원에게만 걸려 있어({@code uq_users_email_active}, V9) 재가입 INSERT 가 막히지 않는다.</p>
	 */
	Optional<User> findByEmailAndDeletedAtIsNull(String email);

	/** 상태별 회원 목록. 관리자 제재 회원 조회에 사용한다({@code idx_users_status} 를 탄다). */
	Page<User> findByStatus(UserStatus status, Pageable pageable);

}
