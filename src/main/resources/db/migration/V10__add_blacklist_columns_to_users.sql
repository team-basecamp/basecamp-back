-- =============================================================
-- V10. users 에 관리자 제재(강제 로그아웃) 정보 추가
-- =============================================================
-- 배경(#18)
--  - 관리자 제재는 토큰 단위가 아니라 회원 단위로 다룬다. 무상태 JWT 라 서버가 발급된 토큰을 보관하지 않아,
--    관리자가 대상 회원의 jti 를 알 수 없기 때문이다(token_blacklist 는 jti 단위 denylist 로 그대로 둔다).
--  - 제재 목록에 "왜, 언제"를 보여주려면 사유와 시각이 필요하다.
--    token_blacklist.reason 은 토큰 단위 컬럼이라 여기에 쓸 수 없다.
--
-- 두 컬럼은 "현재 제재 정보"만 담는다. 제재 해제 시 NULL 로 되돌린다.
-- 제재 이력 보존이 필요해지면 별도 감사 테이블로 분리한다.
-- (status 컬럼의 BLACKLISTED 값이 제재 여부를 나타내며, 이 컬럼들은 그 부가 정보다)

ALTER TABLE users
    ADD COLUMN blacklist_reason VARCHAR(200) NULL COMMENT '관리자 제재 사유',
    ADD COLUMN blacklisted_at   DATETIME     NULL COMMENT '관리자 제재 일시';
