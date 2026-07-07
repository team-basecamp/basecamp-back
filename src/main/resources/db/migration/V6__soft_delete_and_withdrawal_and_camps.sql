-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V6
--  Base    : V5까지 적용 완료 상태 기준
--  DB      : MySQL 8.0+
--  변경     : 1) users : 탈퇴 관련 컬럼 추가
--              - deleted_at, withdrawal_reason 추가
--             2) posts : 소프트 삭제 시각 추가
--              - deleted_at 추가
--             3) 링크 테이블에서 created_at 제거
--              - post_images / review_images테이블에서 created_at 컬럼 제거
--             4) camps 테이블에 고캠핑(GoCamping) 공공 API 필드명을 그대로 사용한 컬럼 추가
--  참고     : Flyway 는 접속 URL 의 DB 로 자동 실행됨.
--            Workbench 로 직접 테스트할 때는 먼저 camping_db 를 선택할 것.
-- =============================================================
 
 
-- -------------------------------------------------------------
-- 1. users : 탈퇴 관련 컬럼 추가
--    · deleted_at : 탈퇴(soft delete) 처리 시각.
--        기존 WITHDRAWN 상태는 "탈퇴 여부", deleted_at 은 "탈퇴 시점" 을 담당.
--        (상태 + 시각을 분리해 감사/보존기간 계산에 활용)
--        미탈퇴 회원은 NULL.
--    · withdrawal_reason : 탈퇴 사유 (자유 입력).
--        정해진 사유 코드로 관리하려면 나중에 ENUM 또는 코드 FK 로 전환 가능.
--        탈퇴 전에는 NULL.
-- -------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN deleted_at        DATETIME     NULL COMMENT '탈퇴(soft delete) 처리 일시',
    ADD COLUMN withdrawal_reason VARCHAR(500) NULL COMMENT '탈퇴 사유';
 
 
-- -------------------------------------------------------------
-- 2. posts : 소프트 삭제 시각 추가
--    · deleted_at : 게시글 삭제(soft delete) 처리 시각.
--        기존 3-상태(ACTIVE/BLINDED/DELETED) 중 DELETED 로 전환된 시점 기록.
--        미삭제 게시글은 NULL.
-- -------------------------------------------------------------
ALTER TABLE posts
    ADD COLUMN deleted_at DATETIME NULL COMMENT '삭제(soft delete) 처리 일시';
 
 
-- -------------------------------------------------------------
-- 3. 링크 테이블에서 created_at 제거
--    post_images / review_images 는 순수 연결(매핑) 테이블이라
--    개별 연결의 생성 시각을 별도 관리할 필요가 없어 컬럼을 제거한다.
--    (이미지 자체의 시각은 images.created_at 으로 충분)
-- -------------------------------------------------------------
ALTER TABLE post_images
    DROP COLUMN created_at;
 
ALTER TABLE review_images
    DROP COLUMN created_at;
    
-- -------------------------------------------------------------
-- 4. camps : 캠핑장 상세정보 컬럼 추가 (전부 NULL 허용)
--
--   [텍스트/목록형]
--   · lineIntro        한줄 소개
--   · homepage         홈페이지 URL
--   · doNm             도(행정구역) — 상세 검색 필터용
--   · sbrsCl           부대시설 목록 (예: 전기,무선인터넷,물놀이장)
--                      → API 가 콤마 구분 문자열로 내려주므로 VARCHAR 로 저장
--   · glampInnerFclty  글램핑 내부시설 (예: TV,에어컨,냉장고)
--   · caravInnerFclty  카라반 내부시설 (예: TV,에어컨,냉장고)
--   · operDeCl         운영일 (예: 평일,주말)
--
--   [개수형]
--   · toiletCo         화장실 개수
--   · swrmCo           샤워실 개수
--   · wtrplCo          개수대 개수
--   · extshrCo         소화기 개수
--     ※ '개수'라 INT 로 정의. 단, GoCamping 원본은 문자열이라
--       비숫자/공백 값이 섞여 올 수 있음 → 적재 시 파싱/정제 필요.
--       원본을 무가공 저장하려면 VARCHAR 로 바꿀 것.
-- -------------------------------------------------------------
ALTER TABLE camps
    ADD COLUMN lineIntro       VARCHAR(500) NULL COMMENT '한줄 소개',
    ADD COLUMN homepage        VARCHAR(255) NULL COMMENT '홈페이지 URL',
    ADD COLUMN doNm            VARCHAR(50)  NULL COMMENT '도(행정구역) - 상세 검색 필터',
    ADD COLUMN sbrsCl          VARCHAR(500) NULL COMMENT '부대시설 목록 (콤마 구분: 전기,무선인터넷,물놀이장 등)',
    ADD COLUMN toiletCo        INT          NULL COMMENT '화장실 개수',
    ADD COLUMN swrmCo          INT          NULL COMMENT '샤워실 개수',
    ADD COLUMN wtrplCo         INT          NULL COMMENT '개수대 개수',
    ADD COLUMN extshrCo        INT          NULL COMMENT '소화기 개수',
    ADD COLUMN glampInnerFclty VARCHAR(500) NULL COMMENT '글램핑 내부시설 (콤마 구분: TV,에어컨,냉장고 등)',
    ADD COLUMN caravInnerFclty VARCHAR(500) NULL COMMENT '카라반 내부시설 (콤마 구분: TV,에어컨,냉장고 등)',
    ADD COLUMN operDeCl        VARCHAR(50)  NULL COMMENT '운영일 (예: 평일,주말)';
        
