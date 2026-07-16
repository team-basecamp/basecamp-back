-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V20
--  변경     : comments 테이블에 노출 상태 컬럼 추가 (게시글 status와 동일 정책)
--             - status : ACTIVE(정상) / BLINDED(관리자 삭제) / DELETED(작성자 삭제)
--               기존 댓글은 모두 정상 노출이므로 DEFAULT 'ACTIVE'
--  참고     : 실제 행은 지우지 않고 상태만 바꾸는 소프트 삭제 방식.
--             BLINDED/DELETED 댓글은 목록에서 안내 문구로 대체 노출한다.
-- =============================================================
ALTER TABLE comments
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE / BLINDED(관리자 삭제) / DELETED(작성자 삭제)' AFTER content;
