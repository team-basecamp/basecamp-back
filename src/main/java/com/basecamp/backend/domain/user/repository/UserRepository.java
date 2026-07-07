package com.basecamp.backend.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.basecamp.backend.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	/**
	 * 이메일로 회원을 조회한다.
	 *
	 * <p>V3에서 {@code provider_id} 가 제거되어, 소셜 회원 식별은 UNIQUE 제약이 있는 {@code email} 로 한다.</p>
	 */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

}
