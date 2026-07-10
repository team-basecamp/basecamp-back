-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V14
--  Base    : V1~V13 적용 완료 상태 기준
--  DB      : MySQL 8.0.16+ (CHECK 제약을 실제로 강제하는 최소 버전)
--  변경     : 1) camp_owner_applications.status 허용값 CHECK 제약 추가
--  참고     : docs/camp-owner-promotion.md, 이슈 #53
-- =============================================================

-- -------------------------------------------------------------
-- 1. camp_owner_applications : status 허용값 강제
--
--    애플리케이션은 @Enumerated(EnumType.STRING) 이라 세 값만 쓴다. 이 제약이 막는 것은
--    raw SQL·수동 패치·다른 클라이언트가 만드는 잘못된 값이다.
--
--    잘못된 값이 들어오면:
--      1) 조회가 죽는다 — Hibernate 가 ApplicationStatus 로 변환하다 예외를 던져
--         관리자 목록 API 가 통째로 실패한다. (쓰기는 막혀 있어도 읽기가 무너진다)
--      2) 파생 컬럼(user_id_pending, business_number_approved)이 NULL 이 되어
--         그 행만 유니크 인덱스에서 조용히 빠진다.
--
--    파생 컬럼은 IF(status = 'PENDING', ...) 처럼 문자열 리터럴에 강결합돼 있다.
--    ApplicationStatus enum 의 상수 이름을 바꾸면 파생 컬럼도 함께 바꿔야 하며,
--    이 CHECK 제약이 그 결합을 DB 차원에서 드러낸다.
--
--    상태를 새로 추가하려면 이 제약을 DROP 하고 다시 만드는 마이그레이션이 필요하다:
--      ALTER TABLE camp_owner_applications DROP CHECK chk_coa_status;
--
--    V1 의 chk_camps_source 와 같은 방식이다.
-- -------------------------------------------------------------
ALTER TABLE camp_owner_applications
    ADD CONSTRAINT chk_coa_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));
