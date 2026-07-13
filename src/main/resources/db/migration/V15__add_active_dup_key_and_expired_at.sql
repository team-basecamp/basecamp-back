-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V15
--  Base    : V1~V14 적용 완료 상태 기준
--  DB      : MySQL 8.0+
--  변경     : 1) reservations 동일인 중복 생성 방지 유니크 제약 추가
--          : 2) reservations 업체 응답 기한(expired_at) 추가
--  참고     : Flyway 는 접속 URL 의 DB 로 자동 실행됨.
--            Workbench 로 직접 테스트할 때는 먼저 camping_db 를 선택할 것.
-- =============================================================

-- -------------------------------------------------------------
-- 1. reservations 동일인 중복 생성 방지 유니크 제약
--    createReservation의 중복 검증(existsOverbookingReservation)은 check-then-act
--    구조라 동일 고객의 동시 요청(더블클릭·재시도)이 검사를 함께 통과하면
--    중복 예약이 저장되고, 선결제 플로우에서 이중 결제로 이어질 수 있다.
-- -------------------------------------------------------------
ALTER TABLE reservations
    ADD COLUMN active_dup_key VARCHAR(150)
        GENERATED ALWAYS AS (
            CASE
                WHEN status IN ('PENDING_PAYMENT', 'PENDING', 'RESERVED')
                    THEN CONCAT(
                        CAST(user_id AS CHAR), '-',
                        CAST(camp_id AS CHAR), '-',
                        CAST(check_in_date AS CHAR), '-',
                        CAST(check_out_date AS CHAR)
                         )
                ELSE NULL
                END
            ) VIRTUAL
        COMMENT '활성 예약 중복 방지 파생 키(취소/거절 시 NULL로 중복 허용)';
ALTER TABLE reservations
    ADD UNIQUE INDEX uq_rsv_active_dup (active_dup_key);

-- -------------------------------------------------------------
-- 2. reservations : expired_at 추가
--    업체의 응답 기한은 24시간으로 지정
-- -------------------------------------------------------------
ALTER TABLE reservations
    ADD COLUMN expired_at DATETIME NULL COMMENT '업체 응답 기한(결제 확인 +24h). PENDING 진입 시 설정',
    ADD INDEX idx_rsv_status_expired (status, expired_at);