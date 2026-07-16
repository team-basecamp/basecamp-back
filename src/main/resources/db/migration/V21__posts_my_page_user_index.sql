-- =============================================================
--  Version : V21
--  DB      : MySQL 8.0+
--  변경    : 마이페이지 "내가 쓴 게시글" 커서 페이징을 위해 posts 의
--            작성자 인덱스 idx_posts_user 를 정렬 키까지 포함하도록 재정의한다.
--
--  배경    : "내가 쓴 게시글" 조회 쿼리는 아래 형태로 나간다.
--
--              WHERE status = ?
--                AND user_id = ?
--                AND (created_at < ? OR (created_at = ? AND post_id < ?))
--              ORDER BY created_at DESC, post_id DESC
--              LIMIT ?
--
--            기존 idx_posts_user (user_id) 는 선두 컬럼이 user_id 뿐이라
--            작성자로 좁힌 뒤 ORDER BY 를 filesort 로 처리한다.
--            정렬 키(created_at DESC, post_id DESC)를 인덱스에 포함시켜
--            V17 의 목록 인덱스처럼 인덱스 순서대로 읽고 LIMIT 에서 멈추게 한다.
--
--            선두 컬럼이 그대로 user_id 라 posts.user_id FK(fk_posts_user)와
--            작성자 단독 조회도 leftmost prefix 로 계속 커버된다.
-- =============================================================

ALTER TABLE posts
    DROP INDEX idx_posts_user,
    ADD INDEX idx_posts_user (user_id, status, created_at DESC, post_id DESC);
