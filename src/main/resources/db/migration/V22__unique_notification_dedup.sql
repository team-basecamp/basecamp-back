-- =============================================================
--  notifications 중복 발송 방지 유니크 제약 (#92)
--  같은 사용자에게 같은 종류(type)의 알림이 같은 대상(target_id)으로 중복 저장되는 것을 막는다.
--  스케줄러 재실행 / 다중 인스턴스에서 예약 D-1 등 알림이 중복 insert 되지 않도록 하는 최종 방어선.
--
--  target_id 가 NULL 인 알림(전체 공지 등)은 MySQL 유니크 인덱스가 NULL 을 서로 다르게 취급하므로
--  제약 대상이 아니다(공지는 여러 건 허용).
-- =============================================================

-- 1) 제약을 걸기 전에 혹시 남아 있을 수 있는 기존 중복을 제거한다(가장 최근 것만 남김).
--    (V2 가 컬럼 개편 전 백필을 먼저 한 것과 같은 "정리 후 제약" 패턴)
DELETE n1
FROM notifications n1
JOIN notifications n2
  ON n1.user_id = n2.user_id
 AND n1.type    = n2.type
 AND n1.target_id = n2.target_id
 AND n1.target_id IS NOT NULL
 AND n1.notification_id < n2.notification_id;

-- 2) 유니크 제약 추가.
ALTER TABLE notifications
    ADD CONSTRAINT uq_notif_user_type_target UNIQUE (user_id, type, target_id);
