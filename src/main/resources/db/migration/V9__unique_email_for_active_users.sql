-- =============================================================
-- V9. email 유니크 제약을 "활성 회원"에게만 적용
-- =============================================================
-- 배경
--  - 탈퇴는 soft delete(users.deleted_at)라 탈퇴한 회원의 행이 email 을 그대로 점유한다.
--    그래서 같은 소셜 계정으로 재가입하려 해도 uq_users_email 때문에 INSERT 가 막힌다.
--  - 그렇다고 유니크를 없애면 같은 이메일의 활성 회원이 둘 생겨,
--    타 provider 가입 차단(EMAIL_ALREADY_REGISTERED)과 동시 가입 경합의 최종 방어선이 함께 무너진다.
--
-- 해결
--  - MySQL 유니크 인덱스는 NULL 을 중복으로 보지 않는다(NULL 인 행은 몇 개든 허용).
--  - 활성 회원일 때만 email 값을, 탈퇴 회원이면 NULL 을 갖는 생성 컬럼을 두고 거기에 유니크를 건다.
--    → 활성 회원끼리는 email 중복 불가 / 탈퇴 회원은 같은 email 을 몇 개든 보유 가능.
--
-- 재가입 흐름
--  탈퇴(deleted_at 기록) → email_active 가 자동으로 NULL 로 계산됨
--  → 재로그인 시 findByEmailAndDeletedAtIsNull 이 못 찾음 → 신규 회원으로 INSERT (새 user_id)
--  → 옛 행은 한 글자도 건드리지 않으므로 원본 email 이 남아 감사·재가입 제한 정책에 쓸 수 있다.
--
-- email_active 는 엔티티에 매핑하지 않는다. ddl-auto: validate 는 매핑된 컬럼만 검사하므로 문제없고,
-- 생성 컬럼이라 Hibernate 가 INSERT/UPDATE 에 끼워넣어서도 안 된다.

ALTER TABLE users
    DROP INDEX uq_users_email,
    ADD COLUMN email_active VARCHAR(100)
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, email, NULL)) STORED
        COMMENT '활성 회원 email 유니크 보장용 파생 컬럼(탈퇴 시 NULL → 중복 허용)',
    ADD UNIQUE INDEX uq_users_email_active (email_active);
