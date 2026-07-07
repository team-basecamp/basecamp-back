-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V5
--  Base    : V1 + V2 + V3 + V4 적용 완료 상태 기준
--  DB      : MySQL 8.0+
--  변경     : 1) reservations 에 예약자 이름(customer_name), 예약자 전화번호(customer_phone) 컬럼 추가
--  참고     : Flyway 는 접속 URL 의 DB 로 자동 실행됨.
--            Workbench 로 직접 테스트할 때는 먼저 camping_db 를 선택할 것.
-- =============================================================
 
 
-- -------------------------------------------------------------
-- 1. reservations : 예약자 이름, 예약자 전화번호 컬럼 추가
--    예약 시 예약자의 이름, 전화번호 작성칸 추가.
--
--    · 예약자의 이름을 받을 컬럼이 없어서 추가, 필수적인 정보이므로 NOT NULL로 제한
--    · 예약자의 전화번호 또한 이름과 같은 이유로 NOT NULL로 제한
--    · MySQL 에서 컬럼 물리 순서는 기능상 의미가 없어 AFTER 는 생략.
--      특정 위치에 두고 싶으면 `AFTER <컬럼명>` 을 붙일 것.
-- -------------------------------------------------------------
ALTER TABLE reservations
    ADD COLUMN customer_name   VARCHAR(50)  NOT NULL COMMENT '예약자 이름',
    ADD COLUMN customer_phone  VARCHAR(20)  NOT NULL COMMENT '예약자 전화번호';
        
