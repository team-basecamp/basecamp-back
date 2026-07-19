-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V23
--  Base    : V1~V22 적용 완료 상태 기준
--  DB      : MySQL 8.0+
--  변경     : payments 테이블에 포트원(PortOne) V2 연동 컬럼 추가
--  참고     : Flyway 는 접속 URL 의 DB 로 자동 실행됨.
-- =============================================================

-- -------------------------------------------------------------
-- 1. 포트원 연동 식별자 컬럼 추가
--    - pg_payment_id : 우리가 발급해 포트원에 넘기는 결제 건 식별자(고객사 주문번호).
--                      결제창 호출 전에 미리 만들어 두고, 완료 확인/웹훅 때 이 값으로 되찾는다.
--                      전역 유일해야 포트원 쪽에서 결제 건이 겹치지 않는다.
--    - pg_tx_id      : 포트원이 부여하는 결제 시도 식별자(transactionId). 장애 추적용.
--    - pg_provider   : 실제 결제를 처리한 PG사 식별자(테스트 모드에서는 채널의 pgProvider).
-- -------------------------------------------------------------
ALTER TABLE payments
    ADD COLUMN pg_payment_id  VARCHAR(80)  NULL COMMENT '포트원에 전달한 결제 건 ID(고객사 주문번호)' AFTER amount,
    ADD COLUMN pg_tx_id       VARCHAR(80)  NULL COMMENT '포트원 결제 시도 ID(transactionId)'        AFTER pg_payment_id,
    ADD COLUMN pg_provider    VARCHAR(30)  NULL COMMENT '결제를 처리한 PG사 식별자'                  AFTER pg_tx_id,
    ADD COLUMN failure_reason VARCHAR(255) NULL COMMENT '결제 실패 사유(포트원 응답 기준)'            AFTER refunded_at,
    ADD COLUMN updated_at     DATETIME     NULL COMMENT '결제 상태 최종 변경 일시'                   AFTER created_at;

-- -------------------------------------------------------------
-- 2. pg_payment_id 유니크 인덱스
--    NULL 은 유니크 제약에서 중복으로 보지 않으므로, 포트원 연동 이전에 쌓인
--    기존 결제 행(pg_payment_id IS NULL)은 그대로 공존한다.
-- -------------------------------------------------------------
ALTER TABLE payments
    ADD UNIQUE KEY uq_payments_pg_payment_id (pg_payment_id);

-- -------------------------------------------------------------
-- 3. payment_method 를 NULL 허용으로 완화
--    결제창을 띄우기 전(READY) 단계에서는 고객이 어떤 수단을 고를지 알 수 없다.
--    포트원 결제 완료 응답을 받은 시점에 실제 수단으로 채운다.
-- -------------------------------------------------------------
ALTER TABLE payments
    MODIFY COLUMN payment_method VARCHAR(20) NULL COMMENT 'CARD / KAKAO_PAY / NAVER_PAY / TOSS_PAY (결제 완료 시 확정)';

-- -------------------------------------------------------------
-- 4. status 컬럼 주석 갱신 (READY 상태 추가)
--    READY : 결제창 호출용으로 자리만 잡아둔 상태. 아직 돈이 오가지 않았다.
-- -------------------------------------------------------------
ALTER TABLE payments
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PAID'
        COMMENT 'READY / PAID / REFUNDED / FAILED';
