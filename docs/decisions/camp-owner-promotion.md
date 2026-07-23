# 캠핑업체(CAMP_OWNER) 권한 부여 설계

> 캠핑업체와 관리자 계정을 어떻게 만들 것인가에 대한 설계 결정 문서.
> 관련 이슈: [#53](https://github.com/team-basecamp/basecamp-back/issues/53)

## 목차

1. [배경 — 지금 무엇이 비어 있나](#1-배경--지금-무엇이-비어-있나)
2. [관리자(ADMIN) — 가입 경로가 없는 것이 설계다](#2-관리자admin--가입-경로가-없는-것이-설계다)
3. [캠핑업체(CAMP_OWNER) — 두 가지 선택지](#3-캠핑업체camp_owner--두-가지-선택지)
4. [결정: 승격(Promotion) 방식](#4-결정-승격promotion-방식)
5. [승격 시 토큰 처리](#5-승격-시-토큰-처리)
6. [스키마](#6-스키마)
7. [API](#7-api)
8. [ErrorCode 추가](#8-errorcode-추가)
9. [작업 범위](#9-작업-범위)

---

## 1. 배경 — 지금 무엇이 비어 있나

`Role` enum에는 권한이 셋 있다.

```java
public enum Role { CUSTOMER, CAMP_OWNER, ADMIN }
```

그런데 **코드가 만들 수 있는 권한은 `CUSTOMER` 하나뿐이다.** 소셜 최초 로그인 시 호출되는 팩토리가 권한을 하드코딩하기 때문이다.

```java
// User.register()
return User.builder()
        ...
        .role(Role.CUSTOMER)      // 항상 CUSTOMER
        .status(UserStatus.ACTIVE)
        .build();
```

현재 상태를 정리하면 이렇다.

| 권한 | 보호되는 엔드포인트 | 부여 경로 |
|---|---|---|
| `CUSTOMER` | (기본) | ✅ 소셜 로그인 |
| `CAMP_OWNER` | ❌ 없음 | ❌ 없음 |
| `ADMIN` | `/api/v1/admin/**` (`hasRole("ADMIN")`) | ❌ 없음 (V7 더미데이터 9번 회원뿐) |

`CAMP_OWNER`는 enum 선언과 V7 더미데이터 외에 코드 어디에서도 참조되지 않는다.

### 그래도 스키마에는 자리가 있다

`camps` 테이블(V1)을 보면 캠핑장의 출처가 두 갈래로 설계되어 있다.

```sql
content_id  BIGINT  COMMENT '고캠핑 API contentId (외부 연동)',
owner_id    BIGINT  COMMENT '캠핑업체 소유자 (직접 등록 시)',

CONSTRAINT chk_camps_source
    CHECK ( (content_id IS NOT NULL) <> (owner_id IS NOT NULL) ),

CONSTRAINT fk_camps_owner
    FOREIGN KEY (owner_id) REFERENCES users (user_id) ON DELETE RESTRICT
```

`<>`는 XOR이다. 캠핑장은 **공공데이터(고캠핑)에서 수집된 것**이거나 **업체가 직접 등록한 것**, 정확히 둘 중 하나다. 즉 `CAMP_OWNER`가 들어올 자리는 스키마에 이미 파여 있고, **채우는 코드만 없다.**

---

## 2. 관리자(ADMIN) — 가입 경로가 없는 것이 설계다

`SecurityConfig`가 `/api/v1/admin/**`를 `hasRole("ADMIN")`으로 막고 있지만, ADMIN 계정을 만드는 엔드포인트는 존재하지 않는다. **이것은 누락이 아니라 방어다.**

관리자 셀프 가입 엔드포인트는 그 자체로 권한 상승(privilege escalation) 취약점이다. 어떤 검증을 붙이든(초대 코드, 시크릿 헤더, 이메일 도메인 화이트리스트) 그 검증값이 유출되는 순간 누구나 관리자가 된다. 그리고 그 값은 반드시 유출된다.

**결정: ADMIN은 API로 만들지 않는다.**

- 최초 관리자는 **Flyway 마이그레이션 시딩** 또는 DB 직접 주입으로 생성한다. (현재 V7 더미데이터가 그 역할)
- 이후 관리자 임명이 필요해지면 **기존 관리자만 호출할 수 있는** `/api/v1/admin/**` 하위 엔드포인트로 추가한다.
- 소셜 로그인으로 들어오는 모든 신규 회원은 `User.register()`가 `CUSTOMER`를 강제하므로, **API를 통해 ADMIN이 되는 경로는 원천적으로 존재하지 않는다.**

이 문서의 나머지는 `CAMP_OWNER`를 다룬다.

---

## 3. 캠핑업체(CAMP_OWNER) — 두 가지 선택지

### 선택지 A. 별도 ID/PW 로그인

업체 전용 로컬 계정을 만든다. `Provider.LOCAL`을 추가하고 `users`에 `password` 컬럼을 붙인다.

현재 스키마는 이 방향을 **적극적으로 배제**하고 있다.

- `users`에 `password` 컬럼이 **아예 없다.**
- `users.provider`는 `NOT NULL`이고 `Provider` enum은 `KAKAO / GOOGLE / NAVER`뿐이다.

이건 "아직 안 만든" 게 아니라 의도적 선택이었다. 비밀번호를 저장하지 않으면 해싱 정책, 재설정 플로우, 복잡도 규칙, 유출 대응, 무차별 대입 방어가 통째로 사라진다.

되돌리려면 `provider` NOT NULL 완화, `password` 컬럼 추가, 로컬 인증 필터 분기, 비밀번호 재설정 API가 전부 따라온다. 그리고 그 뒤로 **인증 경로를 영구히 두 벌 유지**해야 한다.

### 선택지 B. 승격(Promotion)

업체도 **소셜로 `CUSTOMER`로 가입**한다. 그 뒤 사업자 정보를 제출하고, 관리자가 승인하면 `role`이 `CAMP_OWNER`로 승격된다.

### 비교

| 항목 | A. 별도 ID/PW | B. 승격 ✅ |
|---|---|---|
| 로그인 수단 | 새로 구현 | 기존 소셜 그대로 |
| 스키마 변경 | `provider` 완화 + `password` 추가 | 신청 테이블 1개 추가 |
| 인증 경로 | 2벌 (소셜 + 로컬) | 1벌 |
| 비밀번호 관리 비용 | 부활 | 없음 |
| 토큰/제재/탈퇴 로직 | 로컬 계정용으로 재검증 필요 | 100% 재사용 |
| 사업자 실체 검증 | 별도로 필요 | 별도로 필요 (동일) |

---

## 4. 결정: 승격(Promotion) 방식

**핵심 논거: 우리가 검증해야 하는 것은 로그인 수단이 아니라 사업자 실체다.**

업체 계정에 ID/PW를 붙인다고 해서 "이 사람이 진짜 그 캠핑장 사업자인가"가 검증되지 않는다. 그건 **사업자등록번호 심사**로 확인하는 것이지 비밀번호로 확인하는 게 아니다. 로그인 수단을 새로 만드는 비용은 순수한 낭비다.

부차적으로:

- 캠핑장 사장님도 카카오는 쓴다. 소셜 로그인이 진입 장벽이 되지 않는다.
- 승격 방식은 **새로운 인증 메커니즘을 하나도 요구하지 않는다.** 토큰 회전, 블랙리스트, 제재, 탈퇴가 전부 그대로 동작한다.
- 신청 이력이 테이블에 남아 감사(audit)가 된다. "누가 언제 누구를 승인했는가"를 추적할 수 있다.

### 흐름

```
1. 소셜 로그인               → users.role = CUSTOMER
2. 업체 전환 신청 (사업자 정보) → camp_owner_applications.status = PENDING
3. 관리자 승인               → users.role = CAMP_OWNER
                              + UserRevocationCache.revoke(userId)
4. 사용자 재로그인            → 새 access 토큰에 role=CAMP_OWNER
5. 캠핑장 등록 가능           → camps.owner_id = userId
```

---

## 5. 승격 시 토큰 처리

**`role`은 JWT 클레임에 들어간다.** (`JwtTokenProvider.createAccessToken(userId, role)`)

따라서 DB의 `role`을 `CAMP_OWNER`로 바꿔도, 사용자가 들고 있는 access 토큰의 클레임은 여전히 `CUSTOMER`다. 최대 30분(access 토큰 수명) 동안 승격이 반영되지 않는다.

이건 **#18 관리자 제재에서 이미 푼 문제와 동일하다.** 해법도 같다.

```java
// 승격 트랜잭션 안에서
user.promoteToCampOwner();          // users.role = CAMP_OWNER
userRevocationCache.revoke(userId); // 이 시각 이전에 발급된 access 토큰 무효화 (재로그인분은 통과, #119)
```

인증 필터가 매 요청 `isRevoked(userId, iat)`를 확인하므로, 구 토큰은 즉시 거부되고 사용자는 재로그인한다. 재로그인 시 발급되는 토큰에는 `CAMP_OWNER`가 담긴다. **새 인프라가 필요 없다.**

> **#119 보강.** 초기 구현은 `UserRevocationCache`가 "회원 무효화 여부(존재/부재)"만 표시해, 무효화 이후 재로그인해 받은 **새 토큰까지** 거부했다(승격은 제재와 달리 DB status가 정상이라 재로그인이 열려 있다). 이후 마커 값을 **무효화 시각(revokedAt, epoch 초)**으로 바꿔, 필터가 `token.iat < revokedAt`일 때만 거부하도록 고쳤다. 제재는 재로그인이 DB로 막혀 새 토큰이 안 나오므로 종전과 동일하게 동작한다.

### fail-open의 방향성 — 승격과 강등은 다르다

`UserRevocationCache.isRevoked()`는 Redis 조회 실패 시 `false`를 반환한다(fail-open). 승격/강등에서 이 정책의 안전성은 **정반대**다.

| 상황 | Redis 장애 시 구 토큰이 살아남으면 | 위험도 |
|---|---|---|
| **승격** (CUSTOMER → CAMP_OWNER) | 구 토큰은 `CUSTOMER` 권한. 권한이 **낮은** 채로 최대 30분 남을 뿐 | 무해 (권한 상승 없음) |
| **강등** (CAMP_OWNER → CUSTOMER) | 구 토큰은 `CAMP_OWNER` 권한. 회수했어야 할 권한이 최대 30분 유지 | ⚠️ 위험 |

즉 **승격은 fail-open이어도 안전하다.** 최악의 경우 사용자가 30분 뒤에 업체 기능을 쓰게 될 뿐이다.

반면 **강등(승인 취소, 사업자 등록 말소)은 제재(`blacklist`)와 동급으로 다뤄야 한다.** Redis 장애 시 권한이 살아남으면 안 되므로, 강등은 캐시에만 의존하지 말고 `users.status`처럼 **DB에서도 막히는 경로**가 필요하다. 제재가 이미 그렇게 되어 있다 — 소셜 재로그인과 토큰 재발급이 `users.status = BLACKLISTED`로 차단된다.

> 이번 범위에서 **강등은 구현하지 않는다.** 필요해지면 위 근거를 바탕으로 별도 이슈로 다룬다.

---

## 6. 스키마

새 마이그레이션 `V12__add_camp_owner_applications.sql`.

```sql
CREATE TABLE camp_owner_applications (
    application_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '신청 고유 식별자',
    user_id              BIGINT       NOT NULL                COMMENT '신청 회원',
    business_number      CHAR(10)     NOT NULL                COMMENT '사업자등록번호(하이픈 제외 10자리)',
    business_name        VARCHAR(100) NOT NULL                COMMENT '상호명',
    representative_name  VARCHAR(50)  NOT NULL                COMMENT '대표자명',
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
    reject_reason        VARCHAR(200)                         COMMENT '반려 사유(반려 시에만)',
    processed_by         BIGINT                               COMMENT '처리한 관리자',
    processed_at         DATETIME                             COMMENT '처리 일시',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME              ON UPDATE CURRENT_TIMESTAMP,

    -- 한 회원은 심사 중인 신청을 1건만 가질 수 있다.
    -- MySQL 8 에는 부분 인덱스가 없으므로 V9(uq_users_email_active) 와 같은
    -- "파생 컬럼 + UNIQUE" 패턴을 쓴다. PENDING 이 아니면 NULL 이 되어 중복이 허용된다.
    user_id_pending      BIGINT
        GENERATED ALWAYS AS (IF(status = 'PENDING', user_id, NULL)) STORED,

    -- 같은 사업자등록번호로 승인된 업체는 하나뿐이다. (반려/심사중 건은 중복 허용)
    business_number_approved CHAR(10)
        GENERATED ALWAYS AS (IF(status = 'APPROVED', business_number, NULL)) STORED,

    PRIMARY KEY (application_id),
    UNIQUE KEY uq_coa_user_pending      (user_id_pending),
    UNIQUE KEY uq_coa_biznum_approved   (business_number_approved),
    INDEX      idx_coa_status_created   (status, created_at DESC),

    CONSTRAINT fk_coa_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_coa_admin
        FOREIGN KEY (processed_by) REFERENCES users (user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='캠핑업체 권한 승격 신청';
```

### 설계 노트

- **파생 컬럼 + UNIQUE 패턴은 V9에서 이미 쓴 것을 재사용한다.** MySQL의 UNIQUE 인덱스는 NULL을 중복으로 보지 않는다는 성질을 이용해, "특정 상태의 행에만 유니크를 거는" 부분 인덱스를 흉내낸다. 새로운 기법이 아니다.
- 파생 컬럼은 **엔티티에 매핑하지 않는다.** `ddl-auto: validate`는 매핑된 컬럼만 검사하고, 생성 컬럼을 Hibernate가 INSERT/UPDATE에 끼워넣어서도 안 된다. (V9 주석과 동일한 이유)
- `users`에는 컬럼을 추가하지 않는다. 승격은 기존 `role` 컬럼의 값 변경으로 끝난다.
- 반려된 회원의 재신청은 허용된다(`uq_coa_user_pending`이 PENDING 건만 막으므로). 재신청 제한 정책이 필요해지면 `created_at` 기준으로 서비스 레이어에서 판단한다.

---

## 7. API

### 회원 — 업체 전환 신청

```
POST /api/v1/camp-owner/applications      (인증 필요, ROLE_CUSTOMER)
```

```json
{
  "businessNumber": "1234567890",
  "businessName": "베이스캠프 오토캠핑장",
  "representativeName": "홍길동"
}
```

- `201 Created`
- 이미 심사 중인 신청이 있으면 `409` (`CO002`)
- 이미 `CAMP_OWNER`이면 `409` (`CO003`)

```
GET /api/v1/camp-owner/applications/me    (인증 필요)
```

본인의 최신 신청 상태를 조회한다. `200 OK`

### 관리자 — 심사

```
GET    /api/v1/admin/camp-owner/applications?status=PENDING   (ROLE_ADMIN)
POST   /api/v1/admin/camp-owner/applications/{id}/approve     (ROLE_ADMIN)
POST   /api/v1/admin/camp-owner/applications/{id}/reject      (ROLE_ADMIN, body: reason)
```

- 목록은 `Pageable`(기본 20건, `createdAt DESC`). 기존 `AdminUserController.findBlacklistedUsers`와 동일한 형태.
- 승인/반려 모두 `204 No Content`.
- 이미 처리된 신청이면 `409` (`CO004`).
- `/api/v1/admin/**`는 `SecurityConfig`가 이미 `hasRole("ADMIN")`으로 막고 있으므로 **추가 설정이 필요 없다.**

### 승인 트랜잭션

```java
@Transactional
public void approve(Long applicationId, Long adminId) {
    CampOwnerApplication application = ...;   // PENDING 아니면 CO004
    User user = ...;                          // 없거나 탈퇴면 U002

    application.approve(adminId, clock);
    user.promoteToCampOwner();                // role = CAMP_OWNER
    userRevocationCache.revoke(user.getId()); // 구 access 토큰 무효화
}
```

- `revoke()`는 실패 시 예외를 전파한다. 캐시 쓰기가 실패하면 승격 트랜잭션도 함께 롤백되어야 한다 — **"승격됐는데 구 토큰이 살아 있는" 어중간한 상태를 만들지 않는다.** (`AdminUserService.blacklistUser`와 같은 정책)

---

## 8. ErrorCode 추가

기존 `ErrorCode` enum에 `CO` 접두사로 추가한다.

| 코드 | HTTP | 메시지 |
|---|---|---|
| `CO001` | 404 | 업체 전환 신청을 찾을 수 없습니다. |
| `CO002` | 409 | 이미 심사 중인 신청이 있습니다. |
| `CO003` | 409 | 이미 캠핑업체로 등록된 회원입니다. |
| `CO004` | 409 | 이미 처리된 신청입니다. |

사업자등록번호는 요청 DTO의 `@Pattern(\d{10})`으로 **자릿수만** 검증한다(실패 시 400 `C001`). 체크섬은 보지 않는다 — 오타를 걸러낼 뿐 사업자가 실재하는지는 알려주지 못하고, 그 판단은 어차피 관리자 심사가 한다. **국세청 진위 확인 API 연동은 이번 범위 밖이다.**

---

## 9. 작업 범위

### 포함 — 구현 완료

- [x] `V12__add_camp_owner_applications.sql`
- [x] `CampOwnerApplication` 엔티티 + `ApplicationStatus` enum + 리포지토리
- [x] `User.promoteToCampOwner()` — 이미 `CAMP_OWNER`면 `CO003`
- [x] 회원용 신청 API 2개 (`domain/campowner`)
- [x] 관리자용 심사 API 3개 (`domain/admin`)
- [x] `ErrorCode` `CO001`~`CO004` 추가
- [x] 승인 시 `UserRevocationCache.revoke()` 호출 및 롤백 보장

### 구현하며 설계에서 벗어난 점

- **사업자등록번호 체크섬 검증을 넣지 않는다.** 체크섬은 오타만 걸러낼 뿐 사업자 실재 여부를 알려주지 못하고, 실체 판단은 어차피 관리자 심사가 한다. 반면 개발·시연 중 더미 번호(`1234567890` 등)를 막아 마찰만 키운다. 자릿수(`\d{10}`)만 본다. 설계 문서의 `CO005`도 함께 삭제했다.
- **탈퇴·제재 회원은 신청도 승인도 막는다.** 제재를 풀지 않은 채 권한만 올리면 해제되는 순간 업체 권한을 그대로 갖게 된다(`A007`).
- **동시 신청은 `uq_coa_user_pending` 위반을 `CO002`로 변환**한다. `existsByUserIdAndStatus` 조회만으로는 조회와 INSERT 사이의 경합을 막지 못한다.
- **회원 신청 API는 `@PreAuthorize("hasRole('CUSTOMER')")`로 보호한다.** `/api/v1/camp-owner/**`는 `/admin` 아래가 아니라 URL 규칙으로 묶이지 않는다.

### 제외 (별도 이슈)

- 강등(승인 취소) — [5절](#5-승격-시-토큰-처리)의 fail-open 분석 참고. 제재와 동급 설계 필요
- 국세청 사업자등록번호 진위 확인 API 연동
- 증빙 서류 이미지 업로드 (`images` 연관)
- ADMIN 임명 API

> `POST /api/v1/camps/register`는 원래 이 문서의 제외 항목이었으나, 승격 경로가 생겨 `CAMP_OWNER`를 실제로 부여할 수 있게 되었으므로 함께 `@PreAuthorize("hasRole('CAMP_OWNER')")`로 막았다. `camps.owner_id`를 채우는 등록 로직 자체는 기존 구현을 그대로 쓴다.

---

## 참고

- [이슈 #18](https://github.com/team-basecamp/basecamp-back/issues/18) — 관리자 회원 제재. `UserRevocationCache` 도입
- [이슈 #39](https://github.com/team-basecamp/basecamp-back/issues/39) — Refresh Token 회전 및 토큰 블랙리스트 설계
- [docker-setting.md](../guides/docker-setting.md) — Redis 키 구조(`revoke:user:{userId}`)와 fail-open 정책
- `V1__init_schema.sql` — `camps.chk_camps_source` XOR 제약
- `V9__unique_email_for_active_users.sql` — 파생 컬럼 + UNIQUE 패턴의 선례
