-- =============================================================
--  캠핑 플랫폼 DB 수정 마이그레이션
--  Base    : v2 DDL (2026-06-30)
--  DB      : MySQL 8.0+
--  대상    : camps / users / notifications / reviews
--  주의    : 기존 데이터가 있는 환경 기준으로 백필(backfill) 포함
--            Flyway 사용 시 파일명을 V{다음버전}__schema_updates.sql 로 변경
-- =============================================================


-- =============================================================
-- 1. camps : addr2 추가, price INT 로 변경
-- =============================================================
ALTER TABLE camps
    ADD COLUMN addr2 VARCHAR(200) NULL
        COMMENT '상세 주소 (동/호수/건물명 등)' AFTER addr1;

-- price 를 BIGINT -> INT 로 축소.
-- INT 최대값은 2,147,483,647 원. 1박 가격은 이 범위를 넘지 않으므로 안전.
-- (음수가 없으니 필요하면 INT UNSIGNED 로 바꿔 범위/의도를 더 명확히 할 수 있음)
ALTER TABLE camps
    MODIFY COLUMN price INT NOT NULL DEFAULT 0
        COMMENT '1박 기준 가격 (원)';


-- =============================================================
-- 2. users : profile_image 컬럼을 별도 테이블로 분리
-- =============================================================

-- 2-1) 프로필 이미지 전용 테이블 생성 (회원당 1개 = 1:1, user_id UNIQUE)
CREATE TABLE user_images (
    image_id   BIGINT   NOT NULL AUTO_INCREMENT COMMENT '이미지 고유 식별자',
    user_id    BIGINT   NOT NULL               COMMENT '이미지 소유 회원',
    image_url  TEXT     NOT NULL               COMMENT '프로필 이미지 URL',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 일시',
    updated_at DATETIME          ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',

    PRIMARY KEY (image_id),
    UNIQUE KEY uq_user_images_user (user_id),

    CONSTRAINT fk_user_images_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='회원 프로필 이미지 (users.profile_image 에서 분리)';

-- 2-2) 기존 profile_image 데이터 이관
INSERT INTO user_images (user_id, image_url)
SELECT user_id, profile_image
FROM users
WHERE profile_image IS NOT NULL
  AND profile_image <> '';

-- 2-3) users 에서 profile_image 컬럼 제거
ALTER TABLE users
    DROP COLUMN profile_image;


-- =============================================================
-- 3. notifications : ref_id 패턴 -> target_type / target_id 로 개편 (B안)
--    - type        : 알림 이벤트 종류 (기존 유지)   ex) RESERVATION_CONFIRMED
--    - target_type : 알림이 가리키는 대상 종류 (신규) ex) RESERVATION / COMMENT / ANNOUNCEMENT
--    - target_id   : 대상 엔티티 PK (ref_id 개명)     ex) 예약ID / 댓글ID / (공지는 NULL)
--    - FK 없음 : 정합성은 애플리케이션이 책임 (B안 전제)
-- =============================================================
ALTER TABLE notifications
    ADD COLUMN target_type VARCHAR(30) NULL
        COMMENT 'RESERVATION / COMMENT / ANNOUNCEMENT 등 알림 대상 종류' AFTER message,
    CHANGE COLUMN ref_id target_id BIGINT NULL
        COMMENT '대상 엔티티 ID (예약/댓글 등, 전체 공지는 NULL)',
    ADD INDEX idx_notif_target (target_type, target_id);


-- =============================================================
-- 4. reviews : camps 아래로 직접 관계 재설정 (camp_id + FK 추가)
--    기존 reservation_id(체크아웃 검증) 는 그대로 두고,
--    camp_id 를 직접 연결해 캠핑장별 리뷰/평점 집계를 빠르게 함.
-- =============================================================

-- 4-1) camp_id 컬럼 추가 (우선 NULL 허용 - 기존 데이터 백필용)
ALTER TABLE reviews
    ADD COLUMN camp_id BIGINT NULL
        COMMENT '리뷰 대상 캠핑장' AFTER reservation_id;

-- 4-2) 기존 리뷰의 camp_id 를 예약을 통해 채움
UPDATE reviews r
JOIN reservations rs ON r.reservation_id = rs.reservation_id
SET r.camp_id = rs.camp_id;

-- 4-3) NOT NULL 확정 + 인덱스 + FK 추가
ALTER TABLE reviews
    MODIFY COLUMN camp_id BIGINT NOT NULL
        COMMENT '리뷰 대상 캠핑장',
    ADD INDEX idx_reviews_camp (camp_id),
    ADD CONSTRAINT fk_reviews_camp
        FOREIGN KEY (camp_id) REFERENCES camps (camp_id)
            ON DELETE CASCADE;