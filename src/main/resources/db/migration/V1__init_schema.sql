

-- =============================================================
--  캠핑 플랫폼 DB DDL
--  DB      : MySQL 8.0+
--  Charset : utf8mb4 / utf8mb4_unicode_ci
--  작성일  : 2026-06-30
--  버전    : v1.0
-- =============================================================

-- CREATE DATABASE IF NOT EXISTS camping_db
--    DEFAULT CHARACTER SET utf8mb4
--    DEFAULT COLLATE utf8mb4_unicode_ci;

-- USE camping_db;

-- =============================================================
-- 1. users (회원)
-- =============================================================
CREATE TABLE users (
                       user_id       BIGINT          NOT NULL AUTO_INCREMENT COMMENT '회원 고유 식별자',
                       nickname      VARCHAR(50)     NOT NULL               COMMENT '소셜 프로필 기반 닉네임',
                       email         VARCHAR(100)    NOT NULL               COMMENT '소셜 계정 이메일',
                       profile_image TEXT                                   COMMENT '소셜 프로필 이미지 URL',
                       provider      VARCHAR(20)     NOT NULL               COMMENT 'KAKAO / GOOGLE / NAVER',
                       provider_id   VARCHAR(100)    NOT NULL               COMMENT '소셜 플랫폼 고유 ID',
                       role          VARCHAR(20)     NOT NULL DEFAULT 'CUSTOMER'
                           COMMENT 'CUSTOMER / CAMP_OWNER / ADMIN',
                       status        VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                           COMMENT 'ACTIVE / BLACKLISTED / WITHDRAWN',
                       created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '계정 생성 일시',
                       updated_at    DATETIME                 ON UPDATE CURRENT_TIMESTAMP COMMENT '정보 수정 일시',

                       PRIMARY KEY (user_id),
                       UNIQUE KEY uq_users_email          (email),
                       UNIQUE KEY uq_users_provider       (provider, provider_id),
                       INDEX      idx_users_status        (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='소셜 로그인 회원 정보 및 상태 관리';


-- =============================================================
-- 2. token_blacklist (토큰 블랙리스트)
-- =============================================================
CREATE TABLE token_blacklist (
                                 blacklist_id   BIGINT          NOT NULL AUTO_INCREMENT COMMENT '블랙리스트 고유 식별자',
                                 user_id        BIGINT          NOT NULL               COMMENT '대상 회원',
                                 token          TEXT            NOT NULL               COMMENT '블랙리스트 등록된 JWT',
                                 reason         VARCHAR(100)                           COMMENT '계정 정지 / 강제 로그아웃 등',
                                 blacklisted_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '블랙리스트 등록 일시',
                                 expires_at     DATETIME                               COMMENT '토큰 만료 일시',

                                 PRIMARY KEY (blacklist_id),
                                 INDEX idx_bl_user    (user_id),
                                 INDEX idx_bl_expires (expires_at),

                                 CONSTRAINT fk_bl_user
                                     FOREIGN KEY (user_id) REFERENCES users (user_id)
                                         ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='강제 로그아웃 및 회원 탈퇴 시 무효화된 JWT 관리';


-- =============================================================
-- 3. camps (캠핑장)
-- =============================================================
CREATE TABLE camps (
                       camp_id           BIGINT           NOT NULL AUTO_INCREMENT COMMENT '캠핑장 고유 식별자',
                       content_id        BIGINT                                   COMMENT '고캠핑 API contentId (외부 연동)',
                       owner_id          BIGINT                                   COMMENT '캠핑업체 소유자 (직접 등록 시)',
                       faclt_nm          VARCHAR(100)     NOT NULL               COMMENT '캠핑장 이름',
                       addr1             VARCHAR(200)     NOT NULL               COMMENT '도로명/지번 주소',
                       map_x             DECIMAL(11, 8)                          COMMENT 'GPS 경도',
                       map_y             DECIMAL(10, 8)                          COMMENT 'GPS 위도',
                       tel               VARCHAR(20)                             COMMENT '캠핑장 연락처',
                       induty            VARCHAR(100)                            COMMENT '일반야영장, 글램핑, 오토캠핑 등',
                       gnrl_site_co      INT              NOT NULL DEFAULT 0     COMMENT '일반 야영 사이트 수',
                       auto_site_co      INT              NOT NULL DEFAULT 0     COMMENT '오토캠핑 사이트 수',
                       glamp_site_co     INT              NOT NULL DEFAULT 0     COMMENT '글램핑 사이트 수',
                       first_image_url   TEXT                                    COMMENT '캠핑장 대표 사진 URL',
                       manage_sttus      VARCHAR(20)      NOT NULL DEFAULT '운영' COMMENT '운영 / 휴장 / 폐장',
                       average_rating    DECIMAL(3, 2)    NOT NULL DEFAULT 0.00  COMMENT '리뷰 평균 평점 (캐싱 값)',
                       reservation_count INT              NOT NULL DEFAULT 0     COMMENT '누적 예약 건수 (Hot 캠핑장 정렬용)',
                       created_at        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '캠핑장 등록 일시',
                       updated_at        DATETIME                  ON UPDATE CURRENT_TIMESTAMP COMMENT '정보 수정 일시',
                       price             BIGINT           NOT NULL DEFAULT 0     COMMENT '1박 기준 가격 (원)',


                       PRIMARY KEY (camp_id),
                       UNIQUE KEY uq_camps_content_id  (content_id),
                       INDEX      idx_camps_owner      (owner_id),
                       INDEX      idx_camps_rating     (average_rating DESC),
                       INDEX      idx_camps_rsv_count  (reservation_count DESC),
                       INDEX      idx_camps_location   (map_x, map_y),


                       CONSTRAINT chk_camps_source
                           CHECK ( (content_id IS NOT NULL) <> (owner_id IS NOT NULL) ),

                       CONSTRAINT fk_camps_owner
                           FOREIGN KEY (owner_id) REFERENCES users (user_id)
                               ON DELETE RESTRICT          -- SET NULL → RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='고캠핑 Open API 연동 및 캠핑업체 직접 등록 캠핑장 정보';


-- =============================================================
-- 4. camp_wishlists (캠핑장 찜)
-- =============================================================
CREATE TABLE camp_wishlists (
                                wishlist_id BIGINT   NOT NULL AUTO_INCREMENT COMMENT '찜 고유 식별자',
                                user_id     BIGINT   NOT NULL               COMMENT '찜한 회원',
                                camp_id     BIGINT   NOT NULL               COMMENT '찜한 캠핑장',
                                created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '찜 등록 일시',

                                PRIMARY KEY (wishlist_id),
                                UNIQUE KEY uq_wishlist      (user_id, camp_id),
                                INDEX      idx_wish_camp    (camp_id),

                                CONSTRAINT fk_wish_user
                                    FOREIGN KEY (user_id) REFERENCES users (user_id)
                                        ON DELETE CASCADE,
                                CONSTRAINT fk_wish_camp
                                    FOREIGN KEY (camp_id) REFERENCES camps (camp_id)
                                        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='고객이 찜한 캠핑장 목록 (토글 방식 등록/해제)';


-- =============================================================
-- 5. reservations (예약)
-- =============================================================
CREATE TABLE reservations (
                              reservation_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '예약 고유 식별자',
                              user_id        BIGINT       NOT NULL               COMMENT '예약한 고객',
                              camp_id        BIGINT       NOT NULL               COMMENT '예약 대상 캠핑장',
                              check_in_date  DATE         NOT NULL               COMMENT '체크인 일자',
                              check_out_date DATE         NOT NULL               COMMENT '체크아웃 일자',
                              guest_count    INT          NOT NULL DEFAULT 1     COMMENT '예약 인원',
                              status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                                  COMMENT 'PENDING / RESERVED / REJECTED / CANCELLED',
                              reject_reason  VARCHAR(200)                        COMMENT '캠핑업체 거절 사유',
                              cancel_date    DATETIME                            COMMENT '예약 취소 일시',
                              created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '예약 신청 일시',
                              updated_at     DATETIME              ON UPDATE CURRENT_TIMESTAMP COMMENT '상태 변경 일시',
                              total_price    BIGINT       NOT NULL               COMMENT '예약 확정 당시 총 결제 예정 금액 (가격 스냅샷, 원)',


                              PRIMARY KEY (reservation_id),
                              INDEX idx_rsv_user      (user_id),
                              INDEX idx_rsv_camp      (camp_id),
                              INDEX idx_rsv_status    (status),
                              INDEX idx_rsv_dates     (camp_id, check_in_date, check_out_date),
                              INDEX idx_rsv_stat_date (camp_id, created_at DESC),

                              CONSTRAINT fk_rsv_user
                                  FOREIGN KEY (user_id) REFERENCES users (user_id)
                                      ON DELETE CASCADE,
                              CONSTRAINT fk_rsv_camp
                                  FOREIGN KEY (camp_id) REFERENCES camps (camp_id)
                                      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='캠핑장 예약 신청 및 상태 관리';


-- =============================================================
-- 6. payments (결제)
-- =============================================================
CREATE TABLE payments (
                          payment_id     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '결제 고유 식별자',
                          reservation_id BIGINT      NOT NULL               COMMENT '연결된 예약',
                          amount         BIGINT      NOT NULL               COMMENT '결제 금액 (원)',
                          payment_method VARCHAR(20) NOT NULL DEFAULT 'CARD'
                              COMMENT 'CARD / ACCOUNT_TRANSFER 등',
                          status         VARCHAR(20) NOT NULL DEFAULT 'PAID'
                              COMMENT 'PAID / REFUNDED / FAILED',
                          paid_at        DATETIME                           COMMENT '결제 완료 일시',
                          created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '결제 레코드 생성 일시',

                          PRIMARY KEY (payment_id),
                          UNIQUE KEY uq_payments_rsv  (reservation_id),
                          INDEX      idx_pay_status   (status),
                          INDEX      idx_pay_paid_at  (reservation_id, paid_at DESC),

                          CONSTRAINT fk_pay_reservation
                              FOREIGN KEY (reservation_id) REFERENCES reservations (reservation_id)
                                  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Mock 결제 처리 및 매출 통계 기준 데이터';


-- =============================================================
-- 7. posts (게시글)
-- =============================================================
CREATE TABLE posts (
                       post_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '게시글 고유 식별자',
                       user_id      BIGINT       NOT NULL               COMMENT '게시글 작성 회원',
                       category     VARCHAR(30)  NOT NULL DEFAULT 'GENERAL'
                           COMMENT 'GENERAL / CAMP_MATE / RESERVATION_TRANSFER',
                       title        VARCHAR(200) NOT NULL               COMMENT '게시글 제목',
                       content      TEXT         NOT NULL               COMMENT '게시글 본문',
                       view_count   INT          NOT NULL DEFAULT 0     COMMENT '조회수',
                       status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                           COMMENT 'ACTIVE / BLINDED / DELETED',
                       blind_reason VARCHAR(200)                        COMMENT '관리자 블라인드 처리 사유',
                       created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '게시글 작성 일시',
                       updated_at   DATETIME              ON UPDATE CURRENT_TIMESTAMP COMMENT '게시글 수정 일시',

                       PRIMARY KEY (post_id),
                       INDEX idx_posts_user     (user_id),
                       INDEX idx_posts_category (category, status, created_at DESC),
                       INDEX idx_posts_status   (status),

                       CONSTRAINT fk_posts_user
                           FOREIGN KEY (user_id) REFERENCES users (user_id)
                               ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='자유게시판, 캠우모집, 예약양도 카테고리 게시글';


-- =============================================================
-- 8. comments (댓글)
-- =============================================================
CREATE TABLE comments (
                          comment_id BIGINT   NOT NULL AUTO_INCREMENT COMMENT '댓글 고유 식별자',
                          post_id    BIGINT   NOT NULL               COMMENT '연결된 게시글',
                          user_id    BIGINT   NOT NULL               COMMENT '댓글 작성 회원',
                          content    TEXT     NOT NULL               COMMENT '댓글 본문',
                          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '댓글 작성 일시',
                          updated_at DATETIME          ON UPDATE CURRENT_TIMESTAMP COMMENT '댓글 수정 일시',

                          PRIMARY KEY (comment_id),
                          INDEX idx_comments_post (post_id),
                          INDEX idx_comments_user (user_id),

                          CONSTRAINT fk_comments_post
                              FOREIGN KEY (post_id) REFERENCES posts (post_id)
                                  ON DELETE CASCADE,
                          CONSTRAINT fk_comments_user
                              FOREIGN KEY (user_id) REFERENCES users (user_id)
                                  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='게시글에 달린 댓글';


-- =============================================================
-- 9. post_reports (게시글 신고)
-- =============================================================
CREATE TABLE post_reports (
                              report_id   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '신고 고유 식별자',
                              post_id     BIGINT      NOT NULL               COMMENT '신고 대상 게시글',
                              reporter_id BIGINT      NOT NULL               COMMENT '신고한 회원',
                              reason      VARCHAR(50) NOT NULL               COMMENT 'SPAM / INAPPROPRIATE / ILLEGAL 등',
                              description TEXT                               COMMENT '신고 상세 내용',
                              status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                  COMMENT 'PENDING / ACCEPTED / REJECTED',
                              created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신고 접수 일시',

                              PRIMARY KEY (report_id),
                              INDEX idx_report_post   (post_id),
                              INDEX idx_report_status (status, created_at DESC),

                              CONSTRAINT fk_report_post
                                  FOREIGN KEY (post_id) REFERENCES posts (post_id)
                                      ON DELETE CASCADE,
                              CONSTRAINT fk_report_reporter
                                  FOREIGN KEY (reporter_id) REFERENCES users (user_id)
                                      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='게시글 신고 접수 및 관리자 처리 현황';


-- =============================================================
-- 10. reviews (리뷰)
-- =============================================================
CREATE TABLE reviews (
                         review_id      BIGINT         NOT NULL AUTO_INCREMENT COMMENT '리뷰 고유 식별자',
                         reservation_id BIGINT         NOT NULL               COMMENT '연결된 예약 (체크아웃 검증용)',
                         rating         DECIMAL(3, 1)  NOT NULL               COMMENT '1.0 ~ 5.0 평점',
                         content        TEXT           NOT NULL               COMMENT '리뷰 본문',
                         created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '리뷰 작성 일시',
                         updated_at     DATETIME                ON UPDATE CURRENT_TIMESTAMP COMMENT '리뷰 수정 일시',

                         PRIMARY KEY (review_id),
                         UNIQUE KEY uq_reviews_rsv   (reservation_id),


                         CONSTRAINT fk_reviews_reservation
                             FOREIGN KEY (reservation_id) REFERENCES reservations (reservation_id)
                                 ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='예약 체크아웃 이후 작성 가능한 캠핑장 리뷰 및 평점';


-- =============================================================
-- 11. notifications (알림)
-- =============================================================
CREATE TABLE notifications (
                               notification_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '알림 고유 식별자',
                               user_id         BIGINT       NOT NULL               COMMENT '알림 수신 회원',
                               type            VARCHAR(50)  NOT NULL               COMMENT 'RESERVATION_CONFIRMED / CANCELLED / REJECTED 등',
                               message         VARCHAR(300) NOT NULL               COMMENT '알림 본문 텍스트',
                               ref_id          BIGINT                              COMMENT '연관 예약/게시글 ID',
                               is_read         TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '0: 읽지 않음 / 1: 읽음',
                               created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '알림 생성 일시',

                               PRIMARY KEY (notification_id),
                               INDEX idx_notif_user (user_id, is_read, created_at DESC),

                               CONSTRAINT fk_notif_user
                                   FOREIGN KEY (user_id) REFERENCES users (user_id)
                                       ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='예약 확정/취소/거절 등 이벤트 기반 사용자 알림';