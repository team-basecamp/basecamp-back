-- =============================================================
--  Version : V17
--  DB      : MySQL 8.0+
--  변경    : 1) posts 목록 조회를 offset 페이징 → 커서 페이징으로 전환하면서
--               정렬 키에 post_id가 추가됨. 이에 맞춰 인덱스를 재정의한다.
--            2) posts 에 낙관적 락용 version 컬럼 추가
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
ALTER TABLE posts
    DROP INDEX idx_posts_status,
    ADD INDEX idx_posts_status (status, created_at DESC, post_id DESC);


-- -------------------------------------------------------------
-- 2. idx_posts_category : 카테고리별 조회
--    · 기존 (category, status, created_at DESC)
--      → (category, status, created_at DESC, post_id DESC)
--    · 커서의 2차 키인 post_id를 정렬 컬럼 끝에 붙여, 동일 created_at 구간에서도
--      인덱스 순서 == ORDER BY 순서가 유지되게 한다.
-- -------------------------------------------------------------
ALTER TABLE posts
    DROP INDEX idx_posts_category,
    ADD INDEX idx_posts_category (category, status, created_at DESC, post_id DESC);


-- -------------------------------------------------------------
-- 3. posts : version 컬럼 추가
--    게시글 수정/삭제(소프트 삭제)가 동시에 요청될 때 삭제된 글을 덮어쓰는 등
--    상태가 뒤엉키는 것을 막기 위해 JPA @Version 기반 낙관적 락을 적용한다.
--    기존 행은 0으로 초기화.
-- -------------------------------------------------------------
ALTER TABLE posts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전';
