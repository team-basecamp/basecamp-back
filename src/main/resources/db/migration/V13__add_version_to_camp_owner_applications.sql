-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V13
--  Base    : V1~V12 적용 완료 상태 기준
--  DB      : MySQL 8.0+
--  변경     : 1) camp_owner_applications 에 낙관적 락용 version 컬럼 추가
--  참고     : docs/camp-owner-promotion.md, 이슈 #53
-- =============================================================

-- -------------------------------------------------------------
-- 1. camp_owner_applications : version 컬럼 추가
--    관리자 두 명이 같은 신청을 동시에 승인/반려할 때 상태가 뒤엉키는 것을 막는다.
--
--    엔티티의 requirePending() 만으로는 부족하다. findById 는 잠금 없는 스냅샷 읽기라
--    두 트랜잭션이 모두 PENDING 을 보고 검사를 통과한 뒤, 나중에 커밋한 쪽이
--    status / processed_by / processed_at 을 덮어쓴다(lost update).
--    JPA @Version 을 걸면 두 번째 UPDATE 가 0건이 되어 실패한다.
--
--    reservations(V11) 와 같은 방식이며, 기존 행은 0으로 초기화한다.
-- -------------------------------------------------------------
ALTER TABLE camp_owner_applications
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전';
