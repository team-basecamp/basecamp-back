-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V12
--  Base    : V1~V11 적용 완료 상태 기준
--  DB      : MySQL 8.0+
--  변경     : 1) camp_owner_applications 테이블 추가 (캠핑업체 권한 승격 신청)
--  참고     : docs/camp-owner-promotion.md, 이슈 #53
-- =============================================================

-- -------------------------------------------------------------
-- 1. camp_owner_applications : 캠핑업체 권한 승격 신청
--    업체는 소셜로 CUSTOMER 가입 후 사업자 정보를 제출하고,
--    관리자가 승인하면 users.role 이 CAMP_OWNER 로 승격된다.
--    users 에는 컬럼을 추가하지 않는다 (기존 role 값 변경으로 끝난다).
-- -------------------------------------------------------------
CREATE TABLE camp_owner_applications
(
    application_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '신청 고유 식별자',
    user_id             BIGINT       NOT NULL COMMENT '신청 회원',
    business_number     CHAR(10)     NOT NULL COMMENT '사업자등록번호(하이픈 제외 10자리)',
    business_name       VARCHAR(100) NOT NULL COMMENT '상호명',
    representative_name VARCHAR(50)  NOT NULL COMMENT '대표자명',
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
    reject_reason       VARCHAR(200) COMMENT '반려 사유(반려 시에만)',
    processed_by        BIGINT COMMENT '처리한 관리자',
    processed_at        DATETIME COMMENT '처리 일시',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청 일시',
    updated_at          DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    -- 한 회원은 심사 중(PENDING)인 신청을 1건만 가질 수 있다.
    -- MySQL 8 에는 부분 인덱스가 없으므로 V9(uq_users_email_active) 와 같은 "파생 컬럼 + UNIQUE" 패턴을 쓴다.
    -- 유니크 인덱스는 NULL 을 중복으로 보지 않으므로, PENDING 이 아닌 행은 몇 개든 허용된다.
    -- (반려된 회원의 재신청이 막히지 않는다)
    user_id_pending     BIGINT GENERATED ALWAYS AS (IF(status = 'PENDING', user_id, NULL)) STORED
        COMMENT '심사 중 신청 유니크 보장용 파생 컬럼',

    -- 같은 사업자등록번호로 승인된 업체는 하나뿐이다. 반려/심사중 건은 중복을 허용한다.
    business_number_approved CHAR(10) GENERATED ALWAYS AS (IF(status = 'APPROVED', business_number, NULL)) STORED
        COMMENT '승인된 사업자등록번호 유니크 보장용 파생 컬럼',

    PRIMARY KEY (application_id),
    UNIQUE KEY uq_coa_user_pending (user_id_pending),
    UNIQUE KEY uq_coa_biznum_approved (business_number_approved),
    INDEX idx_coa_status_created (status, created_at DESC),

    CONSTRAINT fk_coa_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_coa_admin
        FOREIGN KEY (processed_by) REFERENCES users (user_id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='캠핑업체 권한 승격 신청';
