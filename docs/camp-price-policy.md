# 고캠핑 캠핑장 임시 가격 정책

> 고캠핑 공공데이터 API가 가격 정보를 제공하지 않아, 5만~10만원 사이의 임의 가격을 부여하기로 한 결정과 구현 내역.

## 목차

1. [배경](#1-배경)
2. [검토한 두 방향](#2-검토한-두-방향)
3. [결정: 서비스가 정책을 정하고, 엔티티는 조립만 한다](#3-결정-서비스가-정책을-정하고-엔티티는-조립만-한다)
4. [구현 내역](#4-구현-내역)
5. [기존 데이터 백필](#5-기존-데이터-백필)
6. [코드를 몰라도 테스트해보는 법](#6-코드를-몰라도-테스트해보는-법)
7. [가격 정책을 바꾸고 싶을 때](#7-가격-정책을-바꾸고-싶을-때)
8. [변경된 파일](#8-변경된-파일)

---

## 1. 배경

고캠핑 API 응답(`GocampingApiResponseDto`)에는 가격 필드가 없다. 그런데 `camps` 테이블의 `price` 컬럼은 `NOT NULL`이라, 검색/정렬/필터(`priceMax`, `priceAsc` 등)가 이 값을 전제로 동작한다. 원래 코드는 이걸 그냥 `0`으로 고정해서 저장했다(`Camp.fromGocampingApi()`).

→ 실제 가격 데이터가 없으니, 서비스 운영을 위해 **5만원~10만원 사이의 임의 값**을 부여하기로 했다.

## 2. 검토한 두 방향

### 방향 A — 엔티티 팩토리에서 직접 생성

`Camp.fromGocampingApi()` 내부에서 바로 `ThreadLocalRandom`으로 값을 만들어 채우는 방식. 이미 이 메서드가 `averageRating=0`, `reservationCount=0` 같은 기본값을 채우고 있어서 자연스러워 보이지만, 문제가 있다.

- "5만~10만원 랜덤"은 데이터 매핑이 아니라 **비즈니스 정책**이다. 정책이 엔티티 안에 박혀있으면, 나중에 정책이 바뀔 때마다(가격대 조정, 캠핑장 유형별 차등 등) 엔티티를 계속 고쳐야 한다.
- 엔티티가 "어떻게 구성되는지"뿐 아니라 "무엇을 결정할지"까지 알게 되어, 계층 책임이 흐려진다.

### 방향 B — 서비스에서 결정

`CampService`에서 가격 값을 만들어 엔티티에 넘기는 방식. 다만 `Camp`는 `@Builder` + `@Getter`만 있고 **setter가 없다** (의도적으로 불변에 가깝게 설계됨). 그래서 두 가지 세부 선택지가 있었다.

- **B-1. 엔티티에 범용 setter 추가**: `camp.setPrice(...)`. 구현은 제일 쉽지만, 이 엔티티에만 있는 "값 마음대로 바꾸기" 구멍이 생기고, 다른 필드에도 setter를 요구하는 선례가 된다.
- **B-2. 엔티티 팩토리가 값을 파라미터로 받는다**: `Camp.fromGocampingApi(dto, price)`. 값을 "결정"하는 건 서비스, "조립"하는 건 여전히 엔티티.

## 3. 결정: 서비스가 정책을 정하고, 엔티티는 조립만 한다 (B-2)

**"가격 정책(범위, 규칙)은 비즈니스 로직이라 서비스가 결정하고, 엔티티는 그 값을 받아서 조립만 한다."** — 계층 책임을 한 문장으로 설명할 수 있는 게 이 방향을 고른 이유다.

- **설명하기 쉬움**: 코드 리뷰에서 "왜 여기 있어요?"라는 질문에 바로 답이 된다.
- **테스트/빌드 안정성**: [`.claude/rules/testing.md`](../.claude/rules/testing.md) 규칙상 Service는 순수 단위테스트 대상이라, 가격 생성 로직을 독립적으로 검증할 수 있다. 엔티티는 setter 없는 기존 구조를 그대로 유지해서 기존 코드에 미치는 영향이 적다.
- **확장성**: 가격 범위를 `application.yml`의 `@Value`로 뺐기 때문에, 정책이 바뀌어도 서비스 쪽 설정값만 고치면 된다. 이미 `gocamping.api.key`도 같은 패턴이라 컨벤션이 일치한다.

## 4. 구현 내역

### `Camp.java` — 엔티티는 값을 받아서 조립만

```java
// price: 고캠핑 API가 가격 정보를 제공하지 않아, 서비스 계층에서 정책에 따라 결정한 값을 받아 조립만 한다.
public static Camp fromGocampingApi(GocampingApiResponseDto dto, int price) {
    return Camp.builder()
            // ... 기존 필드 매핑 동일 ...
            .price(price)
            .build();
}

// 가격이 아직 채워지지 않은(0) 캠핑장에 한해서만 값을 채운다. 실제 가격이 있는 캠핑장은 보호한다.
public void assignDefaultPriceIfMissing(int price) {
    if (this.price == 0) {
        this.price = price;
    }
}
```

`assignDefaultPriceIfMissing`은 범용 setter가 아니라 "가격이 없을 때만 채운다"는 가드 조건이 들어간 의도가 명확한 도메인 메서드다. 5절의 백필 기능에서만 쓰인다.

### `CampService.java` — 정책(범위·규칙)을 결정

```java
@Value("${camp.default-price.min}")
private int defaultPriceMin;

@Value("${camp.default-price.max}")
private int defaultPriceMax;

@Value("${camp.default-price.unit}")
private int defaultPriceUnit;

// 신규 캠핑장 저장 시 호출
private int generateRandomPrice() {
    int steps = (defaultPriceMax - defaultPriceMin) / defaultPriceUnit + 1;
    return defaultPriceMin + ThreadLocalRandom.current().nextInt(steps) * defaultPriceUnit;
}

// 기존에 저장된, 가격이 비어있는(price=0) 캠핑장을 일괄 백필
@Transactional
public int backfillMissingPrices() {
    List<Camp> targets = campRepository.findByContentIdIsNotNullAndPrice(0);
    targets.forEach(camp -> camp.assignDefaultPriceIfMissing(generateRandomPrice()));
    return targets.size();
}
```

- `ThreadLocalRandom`을 쓴 이유: `saveCampsFromApi()`가 `@Async` 이벤트 리스너(`CampDataInitializer`)에서도 호출되기 때문에 스레드 안전한 난수 생성기가 필요하다.
- `backfillMissingPrices()`는 `@Transactional` 안에서 조회한 엔티티를 직접 수정하기 때문에, JPA dirty checking으로 커밋 시점에 자동 반영된다. 별도 `save()`/`saveAll()` 호출이 필요 없다.

### `CampRepository.java` — 백필 대상 조회

```java
// 고캠핑 API로 수집됐지만 가격이 비어있는 캠핑장만 조회.
// owner_id로 사장님이 직접 등록한 캠핑장(contentId 없음, 실제 가격 입력값)은 대상에서 제외된다.
List<Camp> findByContentIdIsNotNullAndPrice(Integer price);
```

`camps` 테이블은 `CHECK(content_id XOR owner_id)` 제약이 있어서, 고캠핑 API로 들어온 캠핑장과 사장님이 직접 등록한 캠핑장이 섞여 있다. `content_id IS NOT NULL` 조건으로 후자(진짜 가격이 입력된 캠핑장)를 백필 대상에서 제외한다.

### `CampController.java` — 관리자용 수동 트리거

```java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/backfill-price")
public ResponseEntity<String> backfillMissingPrices() {
    int updatedCount = campService.backfillMissingPrices();
    return ResponseEntity.ok(updatedCount + "개 캠핑장의 가격이 채워졌습니다");
}
```

기존 `/sync`, `/fetch`와 같은 관리자 트리거 패턴을 그대로 따랐다.

### `application.yml` — 정책 값 (환경변수로 오버라이드 가능)

```yaml
camp:
  default-price:
    min: ${CAMP_DEFAULT_PRICE_MIN:50000}
    max: ${CAMP_DEFAULT_PRICE_MAX:100000}
    unit: ${CAMP_DEFAULT_PRICE_UNIT:1000}
```

## 5. 기존 데이터 백필

새 정책 코드는 **신규로 저장되는 캠핑장**에만 자동 적용된다. 이미 DB에 `price=0`으로 저장돼 있던 기존 캠핑장은 관리자 API를 한 번 호출해서 채워야 한다.

```bash
curl -X POST https://{서버주소}/api/v1/camps/backfill-price \
  -H "Authorization: Bearer {ADMIN 토큰}"
```

- **ADMIN 권한 필요**. 일반 계정 토큰으로는 403.
- **멱등적(idempotent)**: `assignDefaultPriceIfMissing`이 `price == 0`인 캠핑장만 채우기 때문에, 실수로 여러 번 호출해도 이미 채워진 캠핑장은 다시 안 건드린다. 두 번째 호출부터는 `0개 캠핑장의 가격이 채워졌습니다`가 정상 응답이다.
- 로컬 환경(2026-07-13 기준)에서 실행한 결과: 3,031개 캠핑장에 가격이 채워졌고, 재호출 시 0개로 확인됨.

## 6. 코드를 몰라도 테스트해보는 법

이 기능을 처음 보는 사람 기준으로, 위에서 한 걸 그대로 따라 하면 되는 순서다.

> ⚠️ **오해하기 쉬운 부분**: 6-2의 `AdminTokenGeneratorTest`는 DB에 가격을 써넣는 코드가 **아니다.** 실제로 `price` 값을 DB에 쓰는 로직은 [4절](#4-구현-내역)에서 본 `CampService.backfillMissingPrices()`이고, 이건 이미 정식 코드(`src/main/java`)에 들어가 있다. `POST /api/v1/camps/backfill-price`가 `@PreAuthorize("hasRole('ADMIN')")`로 막혀 있어서 **"나 ADMIN 맞다"는 토큰이 있어야 그 API를 호출이라도 할 수 있는데**, 이 프로젝트엔 ADMIN 로그인 경로가 없어서 로컬 검증용 토큰을 임시로 만드는 것뿐이다. 즉 이 테스트 파일은 DB 반영 로직이 아니라 **로컬 전용 인증 우회 수단**이고, 값을 쓰는 코드와는 완전히 별개다.

### 6-1. 서버 실행

```bash
docker compose up -d       # Redis (없어도 동작은 하지만 로그아웃/제재 캐시가 fail-open됨. docker-setting.md 참고)
./gradlew bootRun
```

`http://localhost:8080/swagger-ui.html`이 뜨면 정상.

### 6-2. ADMIN 토큰 준비

이 API는 `@PreAuthorize("hasRole('ADMIN')")`로 막혀 있다. [auth-guide.md 7절](./auth-guide.md#7-swagger에서-테스트하기)에 나온 대로 `POST /api/v1/auth/login/{provider}`로 실제 소셜 로그인을 해서 토큰을 받는 게 원칙이지만, **ADMIN 권한은 가입 경로 자체가 없다**([camp-owner-promotion.md 2절](./camp-owner-promotion.md#2-관리자admin--가입-경로가-없는-것이-설계다)). 즉 실제 ADMIN 계정으로 로그인할 방법이 없으므로, 로컬 검증 목적으로는 토큰을 직접 만드는 게 현재로선 유일한 방법이다.

`src/test/java/com/basecamp/backend/security/` 아래에 아래 파일을 **임시로** 만든다.

```java
package com.basecamp.backend.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

class AdminTokenGeneratorTest {
    @Test
    void printAdminToken() {
        // 로컬에서 JWT_SECRET 환경변수를 따로 지정 안 했다면 application.yml 기본값 그대로 사용
        String secret = System.getenv().getOrDefault(
                "JWT_SECRET",
                "change-this-secret-key-in-env-for-local-development-only");
        JwtProperties props = new JwtProperties(secret, 1800000L, 1209600000L);
        JwtTokenProvider provider = new JwtTokenProvider(props);
        String token = provider.createAccessToken(1L, "ADMIN");
        fail("TOKEN=" + token);   // 일부러 실패시켜서 리포트에 토큰을 남긴다
    }
}
```

```bash
./gradlew test --tests "com.basecamp.backend.security.AdminTokenGeneratorTest"
```

테스트가 **실패하는 게 정상**이다. 실패 메시지 안에 `TOKEN=eyJ...` 형태로 토큰이 찍힌다. 콘솔에 안 보이면 리포트 파일에서 확인:

```
build/test-results/test/TEST-com.basecamp.backend.security.AdminTokenGeneratorTest.xml
```

토큰을 복사했으면 **이 파일은 바로 지운다.** 커밋 대상이 아니다.

> ⚠️ **"유효하지 않은 토큰입니다"(A002)가 뜬다면**: 앱을 띄울 때 사용한 `JWT_SECRET`과, 토큰을 만들 때 코드에 들어간 시크릿 값이 서로 다른 것이다. 앱 실행 터미널과 토큰 생성 터미널의 환경변수가 같은지 확인하거나, 위 코드의 fallback 문자열(`change-this-secret-key-...`)을 실제 실행 환경의 값으로 맞춰야 한다.

### 6-3. Swagger에서 호출

1. `http://localhost:8080/swagger-ui.html` 접속
2. 우측 상단 `Authorize` 🔓 버튼 → 위에서 복사한 토큰 값만(=`Bearer ` 접두사 없이) 붙여넣기 → `Authorize` → `Close`
3. `camp-controller`의 `POST /api/v1/camps/backfill-price` 펼치기 → `Try it out` → `Execute`
4. 응답에 `"N개 캠핑장의 가격이 채워졌습니다"`가 나오면 성공. `N`이 `0`이면 "이미 다 채워져 있다"는 뜻이라 이것도 정상이다.

### 6-4. 결과 확인

같은 Swagger 화면에서 `GET /api/v1/camps/search`나 `GET /api/v1/camps/hot`을 아무 파라미터 없이 실행해서 응답의 `price` 필드가 `50000`~`100000` 사이 값으로 들어있는지 눈으로 확인하면 된다. curl로 보고 싶으면:

```bash
curl -s "http://localhost:8080/api/v1/camps/hot?numOfRows=3" | grep -o '"price":[0-9]*'
```

## 7. 가격 정책을 바꾸고 싶을 때

범위나 단위를 바꾸고 싶으면 코드를 고칠 필요 없이 환경변수만 넣으면 된다.

```bash
CAMP_DEFAULT_PRICE_MIN=30000
CAMP_DEFAULT_PRICE_MAX=150000
CAMP_DEFAULT_PRICE_UNIT=5000
```

**주의**: 정책 값을 바꿔도 이미 백필된 캠핑장의 가격은 소급 변경되지 않는다(`price != 0`이라 `assignDefaultPriceIfMissing`이 건드리지 않음). 기존 값도 다시 바꾸고 싶다면, DB에서 대상 캠핑장의 `price`를 `0`으로 되돌린 뒤 `/backfill-price`를 다시 호출해야 한다.

## 8. 변경된 파일

```
 src/main/java/.../camp/controller/CampController.java   | 14 +++++++++++
 src/main/java/.../camp/entity/Camp.java                 | 12 ++++++++--
 src/main/java/.../camp/repository/CampRepository.java   |  4 ++++
 src/main/java/.../camp/service/CampService.java          | 28 +++++++++++++++++++++-
 src/main/resources/application.yml                       |  7 ++++++
```