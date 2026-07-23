# camp_owner_applications 관계 & FK ON DELETE 정책 정리

> ERD(`docs/reference/erd-v3.png`) 리뷰 중 나온 질문들을 정리한 문서.
> 주제: `camp_owner_applications`–`users` 관계선 2개의 이유, 감사성 엔티티의 연관 매핑 생략 설계, 전체 FK의 삭제 정책.

## 1. `camp_owner_applications` ↔ `users` 관계선이 2개인 이유

Workbench는 **FK 제약 하나당 관계선 하나**를 그린다. 이 테이블에는 `users`로 향하는 FK가 실제로 2개 있다 (`V12__add_camp_owner_applications.sql`).

```sql
CONSTRAINT fk_coa_user
    FOREIGN KEY (user_id) REFERENCES users (user_id)       -- ① 신청한 회원
        ON DELETE RESTRICT,
CONSTRAINT fk_coa_admin
    FOREIGN KEY (processed_by) REFERENCES users (user_id)   -- ② 심사한 관리자
        ON DELETE RESTRICT
```

- **선 ①: `user_id` → `users`** — 승격을 신청한 회원
- **선 ②: `processed_by` → `users`** — 신청을 승인/반려한 관리자

한 `users` 행이 "신청자"와 "심사자"라는 서로 다른 역할로 두 번 참조되기 때문에 관계가 2개다. 자기 참조가 아니라 같은 테이블을 두 컬럼이 각각 가리키는 형태.

> ⚠️ 헷갈리기 쉬운 점: `user_id_pending` 컬럼은 `user_id`에서 파생된 **생성 컬럼**(PENDING일 때만 값이 차는 유니크 보장용)일 뿐 FK가 없어서 관계선을 만들지 않는다. 관계선 2개는 순수하게 `user_id` + `processed_by` 때문.

## 2. 엔티티에서 연관 매핑(`@ManyToOne`)을 생략한 이유

`CampOwnerApplication`과 `TokenBlacklist` 모두 `users`를 가리키는 컬럼을 `@ManyToOne` 없이 **식별자(`Long`)만** 들고 있다. 실수가 아니라 의도된 설계.

```java
// CampOwnerApplication.java
@Column(name = "user_id", nullable = false)
private Long userId;
@Column(name = "processed_by")
private Long processedBy;

// TokenBlacklist.java
@Column(name = "user_id", nullable = false)
private Long userId;
```

두 엔티티 Javadoc에 근거가 명시돼 있다: **"감사(audit) 목적의 참조라 연관 매핑 대신 식별자만 들고 있다."**

### 왜 이렇게 하나
1. **탐색용이 아니라 기록용**: 처리 흐름에서 `User` 객체 그래프를 타고 들어갈 일이 없다. 식별자만으로 충분.
2. **N+1 / 지연로딩 함정 제거**: `@ManyToOne`은 EAGER면 불필요한 조인, LAZY면 프록시·`LazyInitializationException` 문제를 떠안는다. 식별자만 들면 원천 차단.
3. **감사 이력의 독립성**: `processed_by`는 "과거에 누가 심사했나"의 스냅샷. 연관 객체로 물리면 "그 계정의 현재 상태"와 개념이 섞인다. FK 제약(`ON DELETE RESTRICT`)이 무결성은 DB에서 보장.

### 트레이드오프
- 장점: N+1/지연로딩 위험 제거, 경계 명확, 감사 로그로서 단순·견고.
- 단점: 회원 정보가 실제로 필요하면 서비스가 `userId`로 한 번 더 명시 조회해야 함. 그래프 탐색(`application.getUser()...`) 편의는 포기.

> 이 패턴은 `token_blacklist`, `notifications` 등 다른 감사성 테이블도 공유한다.
> 반대로 예약↔캠프처럼 **한 화면에서 늘 함께 조회**하는 관계는 `@ManyToOne`이 더 맞다. 케이스별로 나눠서 판단한 설계.

## 3. FK ON DELETE 정책 전수 정리

전체 FK의 **최종 상태**(후속 마이그레이션의 변경 반영). 대부분 `CASCADE`이고, 예외가 4개.

### 예외: CASCADE가 아닌 FK

| 테이블.컬럼 | 참조 대상 | 정책 | 정의 | 이유 |
|---|---|---|---|---|
| `camps.owner_id` | `users` | **RESTRICT** | V1 | 소유자 있는 캠핑장은 회원 삭제 차단 (`SET NULL → RESTRICT`로 변경 흔적) |
| `camp_owner_applications.user_id` | `users` | **RESTRICT** | V12 | 승격 신청 이력 보존 (감사) |
| `camp_owner_applications.processed_by` | `users` | **RESTRICT** | V12 | 심사한 관리자 이력 보존 |
| `users.image_id` | `images` | **SET NULL** | V3 | 이미지 삭제 시 회원은 남기고 프로필 참조만 비움 |

### 나머지: 전부 CASCADE

| 부모 삭제 | 함께 삭제되는 자식 (FK) |
|---|---|
| `users` | `token_blacklist.user_id`, `camp_wishlists.user_id`, `reservations.user_id`, `posts.user_id`, `comments.user_id`, `post_reports.reporter_id`, `notifications.user_id` |
| `camps` | `camp_wishlists.camp_id`, `reservations.camp_id`, `reviews.camp_id`, `camp_images.camp_id` |
| `reservations` | `payments.reservation_id`, `reviews.reservation_id` |
| `posts` | `comments.post_id`, `post_reports.post_id`, `post_images.post_id` |
| `reviews` | `review_images.review_id` |
| `images` | `post_images.image_id`, `review_images.image_id`, `camp_images.image_id` |

### 정책이 갈리는 기준 (패턴)
1. **소유/종속 데이터 → CASCADE**: 부모가 없으면 존재 의미가 없는 데이터(위시리스트·예약·게시글·댓글·알림·이미지 매핑). 다수가 여기.
2. **보존해야 할 감사 이력 / 참조 보호 → RESTRICT**: `camp_owner_applications`, `camps.owner_id`. 부모 삭제 자체를 막는다.
3. **부모는 남기고 참조만 정리 → SET NULL**: `users.image_id`.

## 4. 유의 사항 — 소프트 삭제 여부

`token_blacklist.user_id`는 **CASCADE**지만, 이 정책들은 실제 `DELETE` 쿼리가 나갈 때만 작동한다.
회원 탈퇴를 **소프트 삭제**(`users.deleted_at`, `status`)로 처리하면 CASCADE/RESTRICT는 발동하지 않고,
"탈퇴 시 블랙리스트/신청 이력이 어떻게 되는가"는 DB 제약이 아니라 **애플리케이션 서비스 로직**이 결정한다.
→ 탈퇴가 하드/소프트 중 무엇인지에 따라 실제 동작이 달라지므로 별도 확인 필요.
