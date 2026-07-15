-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V19
--  변경     : camps 테이블에 소프트 삭제 컬럼 추가
--             - deleted_at : 캠핑장 삭제(soft delete) 처리 시각
--               미삭제 캠핑장은 NULL
-- =============================================================
ALTER TABLE camps
    ADD COLUMN deleted_at DATETIME NULL COMMENT '삭제(soft delete) 처리 일시';