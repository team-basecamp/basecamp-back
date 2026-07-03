-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V3
--  Base    : V1 + V2 적용 완료 상태 기준
--  DB      : MySQL 8.0+
--  변경     : 1) users.provider_id 제거
--            2) user_images -> images 로 테이블명 변경 (공용 이미지 저장소)
--            3) 이미지 참조 방향 반전
--               기존) images.user_id -> users        (images 가 FK 보유)
--               변경) users.image_id -> images        (users 가 FK 보유, 1:1)
--                     posts / reviews 는 다중 이미지 -> 중간 테이블(LIST)로 연결
--  참고     : Flyway 는 접속 URL 의 DB 로 자동 실행됨.
--            Workbench 로 직접 테스트할 때는 먼저 camping_db 를 선택할 것.
-- =============================================================


-- -------------------------------------------------------------
-- 1. users : provider_id 컬럼 제거
--    provider_id 는 복합 유니크 uq_users_provider (provider, provider_id)
--    에 포함돼 있어, 컬럼만 지우면 유니크가 provider 단독으로 축소된다.
--    → 유니크 키를 먼저 제거한 뒤 컬럼을 삭제한다.
-- -------------------------------------------------------------
ALTER TABLE users
DROP INDEX uq_users_provider,
DROP COLUMN provider_id;


-- -------------------------------------------------------------
-- 2. user_images -> images 로 테이블명 변경
--    이제 프로필/게시글/리뷰 이미지를 모두 총괄하는 공용 저장소이므로
--    이름을 images 로 일반화한다.
--    ※ 테이블명을 바꿔도 V2 에서 생성된 제약/인덱스 이름
--      (fk_user_images_user, uq_user_images_user) 은 그대로 유지되므로,
--      아래에서 제거할 때는 기존 이름 그대로 참조한다.
-- -------------------------------------------------------------
RENAME TABLE user_images TO images;


-- -------------------------------------------------------------
-- 3. 이미지 참조 방향 반전
--    images 는 참조당하는 쪽(공용 저장소)이 되고,
--    참조하는 쪽(users / post_images / review_images)이 FK 를 갖는다.
-- -------------------------------------------------------------

-- 3-1) users 에 image_id(1:1 FK) 추가 후, 기존 프로필 이미지를 백필.
--      UNIQUE 로 "회원당 이미지 1개" 유지 (NULL 허용 = 프로필 없는 회원도 가능)
ALTER TABLE users
    ADD COLUMN image_id BIGINT NULL
        COMMENT '프로필 이미지 (images 참조, 회원당 1개)' AFTER email,
    ADD UNIQUE KEY uq_users_image (image_id),
    ADD CONSTRAINT fk_users_image
        FOREIGN KEY (image_id) REFERENCES images (image_id)
            ON DELETE SET NULL;

UPDATE users u
    JOIN images ui ON ui.user_id = u.user_id
    SET u.image_id = ui.image_id;

-- 3-2) images 에서 users 로 향하던 관계 제거 (방향 반전 완료)
--      결과: images(image_id, image_url, created_at, updated_at)
ALTER TABLE images
DROP FOREIGN KEY fk_user_images_user,
DROP INDEX uq_user_images_user,
DROP COLUMN user_id;


-- -------------------------------------------------------------
-- 4. post_images : 게시글 <-> 이미지 중간 테이블 (1 게시글 : N 이미지)
-- -------------------------------------------------------------
CREATE TABLE post_images (
                             post_id    BIGINT   NOT NULL COMMENT '대상 게시글',
                             image_id   BIGINT   NOT NULL COMMENT '연결된 이미지',
                             sort_order INT      NOT NULL DEFAULT 0 COMMENT '이미지 정렬 순서 (LIST 순서)',
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '연결 일시',

                             PRIMARY KEY (post_id, image_id),
                             INDEX idx_post_images_image (image_id),

                             CONSTRAINT fk_post_images_post
                                 FOREIGN KEY (post_id) REFERENCES posts (post_id)
                                     ON DELETE CASCADE,
                             CONSTRAINT fk_post_images_image
                                 FOREIGN KEY (image_id) REFERENCES images (image_id)
                                     ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='게시글-이미지 연결 (1 게시글 : N 이미지, LIST)';


-- -------------------------------------------------------------
-- 5. review_images : 리뷰 <-> 이미지 중간 테이블 (1 리뷰 : N 이미지)
-- -------------------------------------------------------------
CREATE TABLE review_images (
                               review_id  BIGINT   NOT NULL COMMENT '대상 리뷰',
                               image_id   BIGINT   NOT NULL COMMENT '연결된 이미지',
                               sort_order INT      NOT NULL DEFAULT 0 COMMENT '이미지 정렬 순서 (LIST 순서)',
                               created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '연결 일시',

                               PRIMARY KEY (review_id, image_id),
                               INDEX idx_review_images_image (image_id),

                               CONSTRAINT fk_review_images_review
                                   FOREIGN KEY (review_id) REFERENCES reviews (review_id)
                                       ON DELETE CASCADE,
                               CONSTRAINT fk_review_images_image
                                   FOREIGN KEY (image_id) REFERENCES images (image_id)
                                       ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='리뷰-이미지 연결 (1 리뷰 : N 이미지, LIST)';