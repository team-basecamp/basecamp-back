-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V18
--  Base    : V1~V17 적용 완료 상태 기준
--  DB      : MySQL 8.0+
--  변경     : post_reports 에 "한 회원이 같은 글을 PENDING 상태로 1건만 신고" 부분 유니크 제약 추가
--
--  배경     : PostService.report() 는 exists 선검사 후 save 한다. 그 사이에는 같은 회원의
--             동시 이중 신고(더블클릭 등) 경쟁 구간이 있어, 애플리케이션 검사만으로는
--             중복 PENDING 신고를 원자적으로 막지 못한다. DB 유니크 제약을 최종 방어선으로 둔다.
--
--  방식     : MySQL 8 에는 부분 인덱스가 없으므로 V9(uq_users_email_active),
--             V12(uq_coa_user_pending) 와 동일한 "파생 컬럼 + UNIQUE" 패턴을 쓴다.
--             status='PENDING' 일 때만 (post_id, reporter_id) 를 파생 컬럼에 채우고 그 외에는 NULL.
--             유니크 인덱스는 NULL 을 중복으로 보지 않으므로, 처리 완료(ACCEPTED/REJECTED)된
--             과거 신고는 재신고를 막지 않는다. (status 를 그대로 유니크 키에 넣으면 REJECTED 가
--             2건 생길 때 관리자의 두 번째 반려가 충돌하므로, 통짜 유니크 대신 이 방식을 쓴다.)
--
--  주의     : 파생 컬럼은 반드시 VIRTUAL 로 만든다. STORED 로 하면 MySQL 8 이
--             "ON DELETE CASCADE FK 가 걸린 base 컬럼(post_id, reporter_id)을 STORED 생성
--             컬럼의 소스로 쓸 수 없다"며 ALTER 를 거부한다. (V12 는 FK 가 RESTRICT 라 STORED 가능)
--             VIRTUAL 인덱스 컬럼에는 이 제약이 ON DELETE CASCADE 에 적용되지 않고,
--             InnoDB 는 VIRTUAL 컬럼에도 UNIQUE 인덱스를 지원한다.
-- =============================================================

ALTER TABLE post_reports
    ADD COLUMN post_id_pending BIGINT
        GENERATED ALWAYS AS (IF(status = 'PENDING', post_id, NULL)) VIRTUAL
        COMMENT 'PENDING 중복 신고 방지용 파생 컬럼(대상 게시글)',
    ADD COLUMN reporter_id_pending BIGINT
        GENERATED ALWAYS AS (IF(status = 'PENDING', reporter_id, NULL)) VIRTUAL
        COMMENT 'PENDING 중복 신고 방지용 파생 컬럼(신고 회원)',
    -- 두 파생 컬럼은 status 조건이 같아 항상 함께 NULL 이거나 함께 non-null 이다.
    -- non-null 인 PENDING 행끼리만 (post_id, reporter_id) 유일성이 강제된다.
    ADD UNIQUE KEY uq_report_pending (post_id_pending, reporter_id_pending);
