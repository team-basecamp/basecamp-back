package com.basecamp.backend.domain.user.repository;

import com.basecamp.backend.common.enums.Role;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.entity.UserStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * 관리자 회원 목록 조회의 검색 조건(상태/권한/키워드)을 조합하기 위한 Specification 모음(#19).
 *
 * <p>조건이 비어 있으면 {@code null} 을 돌려준다. {@code Specification.and(null)} 은 해당 조건을 무시하므로, 호출부는 파라미터 유무를
 * 분기하지 않고 그대로 이어붙일 수 있다({@code CampSpecs} 와 같은 방식).
 */
public class UserSpecs {

  private UserSpecs() {}

  /** 회원 상태 필터. 지정하지 않으면 탈퇴 회원을 포함한 전체가 조회된다. {@code idx_users_status} 를 탄다. */
  public static Specification<User> statusEquals(UserStatus status) {
    if (status == null) return null;
    return (root, query, cb) -> cb.equal(root.get("status"), status);
  }

  public static Specification<User> roleEquals(Role role) {
    if (role == null) return null;
    return (root, query, cb) -> cb.equal(root.get("role"), role);
  }

  /**
   * 닉네임 또는 이메일 부분 일치.
   *
   * <p>앞 와일드카드(`%keyword%`) 때문에 인덱스를 타지 못하고 풀 스캔이 된다. 관리자 전용 화면이고 회원 규모가 크지 않아 감수한다. 회원 수가 늘어 문제가
   * 되면 전문 검색 인덱스로 옮긴다.
   */
  public static Specification<User> keywordContains(String keyword) {
    if (keyword == null || keyword.isBlank()) return null;
    String like = "%" + escapeLike(keyword.trim()) + "%";
    return (root, query, cb) ->
        cb.or(cb.like(root.get("nickname"), like, '\\'), cb.like(root.get("email"), like, '\\'));
  }

  /** 사용자가 넣은 LIKE 메타문자(`%`, `_`)를 리터럴로 취급한다. */
  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
