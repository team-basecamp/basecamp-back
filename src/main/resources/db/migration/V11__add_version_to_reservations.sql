-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V11
--  Base    : V1~V10 적용 완료 상태 기준
--  DB      : MySQL 8.0+
--  변경     : 1) reservations 에 낙관적 락용 version 컬럼 추가
--  참고     : Flyway 는 접속 URL 의 DB 로 자동 실행됨.
--            Workbench 로 직접 테스트할 때는 먼저 camping_db 를 선택할 것.
-- =============================================================

-- -------------------------------------------------------------
-- 1. reservations : version 컬럼 추가
--    업체의 예약 수락/거절이 동시에 요청될 때 상태가 뒤엉키는 것을 막기 위해
--    JPA @Version 기반 낙관적 락을 적용한다. 기존 행은 0으로 초기화.
-- -------------------------------------------------------------
ALTER TABLE reservations
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전';

-- -------------------------------------------------------------
-- 2. reservations : cancel_date -> cancel_at으로 변경
--    통일성을 위해 data를 at으로 변경
-- -------------------------------------------------------------
ALTER TABLE reservations
    CHANGE COLUMN cancel_date cancel_at DATETIME NULL COMMENT '예약 취소 일시';