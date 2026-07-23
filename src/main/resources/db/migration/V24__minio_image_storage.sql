-- =============================================================
--  캠핑 플랫폼 DB 마이그레이션 V24
--  Base    : V1~V23 적용 완료 상태 기준
--  DB      : MySQL 8.0+
--  변경     : 이미지 저장소를 로컬 디스크에서 MinIO 로 전환
--            1) images 에 저장 방식(storage_type)과 객체 키(object_key) 추가
--            2) 로컬 디스크에 있던 기존 업로드 행 정리
--            3) camp_images 신설 (직접 등록 캠핑장의 다중 이미지)
--  참고     : Flyway 는 접속 URL 의 DB 로 자동 실행됨.
-- =============================================================


-- -------------------------------------------------------------
-- 1. images : 저장 방식과 객체 키 컬럼 추가
--
--    image_url 한 컬럼에 두 종류의 값이 섞여 들어온다.
--      EXTERNAL : 소셜 로그인(카카오/구글/네이버)이 준 프로필 이미지의 외부 절대 URL.
--                 우리가 소유하지 않으므로 삭제 대상이 아니고 object_key 도 없다.
--      MINIO    : 우리가 업로드한 이미지의 공개 URL.
--                 (예: http://localhost:9000/basecamp/posts/ab12….jpg)
--
--    두 경우 모두 image_url 은 그대로 응답에 실린다. 응답 DTO 가 URL 을 조립할 필요가
--    없도록 완성된 절대 URL 을 저장하는 방식이라, 기존 DTO 는 손대지 않는다.
--
--    object_key 는 삭제할 때 쓴다. URL 에서 접두어를 잘라내 유도할 수도 있지만,
--    엔드포인트 설정이 바뀌면 유도가 어긋나므로 저장 시점의 키를 그대로 남긴다.
--
--    기본값을 EXTERNAL 로 둔 이유: 이 시점에 남아 있는 행은 소셜 프로필뿐이다.
--    (로컬 업로드 행은 아래 2번에서 지운다.)
-- -------------------------------------------------------------
ALTER TABLE images
    ADD COLUMN storage_type VARCHAR(20) NOT NULL DEFAULT 'EXTERNAL'
        COMMENT '저장 방식: EXTERNAL(외부 URL 참조) | MINIO(자체 업로드)' AFTER image_id,
    ADD COLUMN object_key   VARCHAR(255) NULL
        COMMENT 'MINIO 일 때 버킷 안의 객체 키 (예: posts/ab12.jpg). EXTERNAL 이면 NULL' AFTER storage_type;


-- -------------------------------------------------------------
-- 2. 로컬 디스크 시절의 업로드 행 정리
--
--    기존 구현은 DB 에 "/images/<파일명>" 상대경로를 남기고 실물은 서버 디스크
--    (uploads/images)에 두었다. MinIO 로 옮기면서 그 파일들은 이관하지 않기로 했으므로,
--    행만 남으면 전부 깨진 링크가 된다. 따라서 여기서 지운다.
--
--    연쇄 효과(V3 에서 정의한 FK 기준):
--      - post_images  / review_images : ON DELETE CASCADE  → 연결 행이 함께 사라진다.
--      - users.image_id               : ON DELETE SET NULL → 프로필이 비워진다.
--    즉 로컬에 업로드했던 프로필 사진은 사라지고, 소셜 프로필(외부 URL)은 그대로 남는다.
--
--    "/images/" 로 시작하는 것만 지운다. 소셜 프로필은 http(s) 로 시작하므로 걸리지 않는다.
-- -------------------------------------------------------------
DELETE FROM images WHERE image_url LIKE '/images/%';


-- -------------------------------------------------------------
-- 3. camp_images : 캠핑장 <-> 이미지 중간 테이블 (1 캠핑장 : N 이미지)
--
--    post_images / review_images (V3) 와 같은 구조다.
--
--    직접 등록한 캠핑장에만 쓴다. 고캠핑 API 로 들여온 캠핑장의 이미지는 외부 URL 이라
--    camps.first_image_url 에 그대로 두고 이 테이블을 쓰지 않는다. 조회 시에는 이 테이블을
--    먼저 보고, 비어 있으면 first_image_url 로 넘어간다.
-- -------------------------------------------------------------
CREATE TABLE camp_images (
                             camp_id    BIGINT   NOT NULL COMMENT '대상 캠핑장',
                             image_id   BIGINT   NOT NULL COMMENT '연결된 이미지',
                             sort_order INT      NOT NULL DEFAULT 0 COMMENT '이미지 정렬 순서 (LIST 순서)',
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '연결 일시',

                             PRIMARY KEY (camp_id, image_id),
                             INDEX idx_camp_images_image (image_id),

                             CONSTRAINT fk_camp_images_camp
                                 FOREIGN KEY (camp_id) REFERENCES camps (camp_id)
                                     ON DELETE CASCADE,
                             CONSTRAINT fk_camp_images_image
                                 FOREIGN KEY (image_id) REFERENCES images (image_id)
                                     ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='캠핑장-이미지 연결 (1 캠핑장 : N 이미지, LIST). 직접 등록 캠핑장 전용';
