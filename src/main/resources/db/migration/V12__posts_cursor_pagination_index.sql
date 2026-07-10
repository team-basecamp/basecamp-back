-- =============================================================
--  Version : V12
--  DB      : MySQL 8.0+
--  변경    : posts 목록 조회를 offset 페이징 → 커서 페이징으로 전환하면서
--            정렬 키에 post_id가 추가됨. 이에 맞춰 인덱스를 재정의한다.
--
--  배경    : 목록 정렬 키는 (created_at DESC, post_id DESC).
--            커서 조건은 아래 형태로 나가며, ORDER BY와 정렬 키가 같아야
--            MySQL이 filesort 없이 인덱스 순서대로 읽고 LIMIT에서 멈춘다.
--
--              WHERE status = ?
--                AND (created_at < ? OR (created_at = ? AND post_id < ?))
--              ORDER BY created_at DESC, post_id DESC
--              LIMIT ?
--
--            기존 인덱스는 post_id가 없어 동일 시각 글이 몰린 구간에서
--            정렬을 인덱스로 끝내지 못하고 filesort로 떨어진다.
-- =============================================================


-- -------------------------------------------------------------
-- 1. idx_posts_status : 전체 카테고리 조회 (category 조건 없음)
--    · 기존 (status) → (status, created_at DESC, post_id DESC)
--    · 기존 인덱스는 status 단일 컬럼이라 정렬·LIMIT을 인덱스로 처리하지 못했다.
--      새 인덱스의 선두 컬럼이 그대로 status라 기존에 이 인덱스를 쓰던
--      status 단독 조회도 leftmost prefix로 계속 커버된다.
-- -------------------------------------------------------------
ALTER TABLE posts DROP INDEX idx_posts_status;

CREATE INDEX idx_posts_status
    ON posts (status, created_at DESC, post_id DESC);


-- -------------------------------------------------------------
-- 2. idx_posts_category : 카테고리별 조회
--    · 기존 (category, status, created_at DESC)
--      → (category, status, created_at DESC, post_id DESC)
--    · 커서의 2차 키인 post_id를 정렬 컬럼 끝에 붙여, 동일 created_at 구간에서도
--      인덱스 순서 == ORDER BY 순서가 유지되게 한다.
-- -------------------------------------------------------------
ALTER TABLE posts DROP INDEX idx_posts_category;

CREATE INDEX idx_posts_category
    ON posts (category, status, created_at DESC, post_id DESC);
