-- =============================================================
-- V8. token_blacklist 조회 키를 JWT 원문 → jti(UUID) 로 전환
-- =============================================================
-- 배경(#39)
--  - 블랙리스트의 핫 패스는 "이 토큰이 블랙리스트에 있는가?" 라는 토큰 기준 조회다.
--    기존 인덱스는 (user_id), (expires_at) 뿐이라 매 검증마다 풀 스캔이 발생하고,
--    TEXT 컬럼은 MySQL 에서 prefix 길이 없이는 인덱스를 걸 수 없다.
--  - JWT 발급 시 jti(UUID) 클레임을 실어 보내고, 블랙리스트는 jti 로 저장·조회한다.
--  - 토큰 원문을 저장하지 않으므로 DB 유출 시 유효한 토큰이 통째로 새는 리스크도 함께 사라진다.
--    token 컬럼은 디버깅 여지를 위해 남기되 NULL 허용으로 완화하고, 조회 키로는 쓰지 않는다.
--
-- token_blacklist 는 V1 생성 이후 어떤 마이그레이션도 INSERT 하지 않아 비어 있으므로,
-- 기본값 없는 NOT NULL 컬럼을 추가해도 안전하다.

ALTER TABLE token_blacklist
    ADD COLUMN jti CHAR(36) NOT NULL COMMENT 'JWT ID (UUID). 블랙리스트 조회 키' AFTER user_id,
    MODIFY COLUMN token TEXT NULL COMMENT '블랙리스트 등록된 JWT 원문(디버깅용, 조회 키 아님)',
    ADD UNIQUE INDEX idx_bl_jti (jti);
