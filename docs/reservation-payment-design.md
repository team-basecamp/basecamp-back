# BaseCamp 예약·결제 도메인 설계 결정 문서

> Claude Code 작업 컨텍스트용. 예약 도메인 구현 과정에서 확정된 설계 결정과
> 결제 연동 시 반영해야 할 사항을 정리한다.
> 프로젝트: Spring Boot 3.3.x / JPA / Flyway / MySQL 8.0 / JWT 인증(구현 완료) / SpringDoc Swagger

---

## 1. 비즈니스 플로우 (확정)

**선결제 + 사업자 승인제**

```
고객: 예약 정보 입력 → 버튼 클릭
  → 서버: 예약 생성 (PENDING_PAYMENT, 결제 대기)   ← 신규 상태, 추가 필요
  → PG 결제창 → 고객 결제
  → PG 웹훅/콜백으로 서버가 결제 확인
  → 예약 상태 PENDING(승인 대기)으로 전이
사업자: 승인(RESERVED) 또는 거절(REJECTED)
고객: PENDING / RESERVED 상태에서 언제든 취소 가능 (CANCELLED)
```

- 결제창이 뜨려면 주문번호가 필요하므로 예약 행은 결제 전에 먼저 생성된다.
- 선결제 구조이므로 거절/취소는 환불을 동반한다 (5절 참고).

## 2. 예약 상태 (ReservationStatus)

| 상태 | 의미 | 비고 |
|---|---|---|
| PENDING_PAYMENT | 결제 대기 | **신규 추가 필요.** 결제창 이탈 예약과 승인 대기 예약을 구분 |
| PENDING | 결제 완료, 사업자 승인 대기 | |
| RESERVED | 사업자 승인, 확정 | |
| REJECTED | 사업자 거절 | rejectReason 필수, 환불 동반 |
| CANCELLED | 고객 취소 (소프트 딜리트) | cancelDate 기록, 환불 동반 |

**상태 전이 규칙**
- PENDING_PAYMENT → PENDING: PG 결제 확인 시
- PENDING_PAYMENT → 만료: 일정 시간(예: 30분) 미결제 시 자동 만료 정책 필요 (미구현, 정책 미확정)
- PENDING → RESERVED / REJECTED: 사업자만
- PENDING, RESERVED → CANCELLED: 고객, 제한 없음 (RESERVED 취소의 환불 정책은 기획 미확정)
- CANCELLED, REJECTED에서 취소 시도 → `ALREADY_CANCELED_OR_REJECTED` (R003)
- 엔티티 비즈니스 메서드: `approve()`, `reject(reason)`, `cancel()` 사용, 더티 체킹으로 UPDATE (save() 불필요)

## 3. 동시성 제어 (3층 방어, 구현 완료)

| 계층 | 방어 대상 | 수단 | 상태 |
|---|---|---|---|
| 생성 시점 | 동일 고객의 활성 예약 기간 겹침 | 서비스 존재 검증 → `DUPLICATE_RESERVATION` (R005, 409) | ✅ |
| 승인 시점 | 타 고객의 확정(RESERVED) 예약 기간 겹침 | 승인 로직 겹침 검증 → `RESERVATION_PERIOD_CONFLICT` (R006, 409) | ⏳ 승인 API 구현 시 |
| 상태 전이 | 동일 예약 건 동시 수정 (승인 vs 취소 등) | 낙관적 락 `@Version` → `CONCURRENT_MODIFICATION` (409) | ✅ |

**기간 겹침 판정식 (표준)**: `기존.checkIn < 신규.checkOut AND 기존.checkOut > 신규.checkIn`
- 부등호에 등호 없음 → 체크아웃 당일 새 체크인 허용 (숙박업 관례)
- 뒤집힌 날짜는 겹침 검증을 항상 통과하므로 날짜 순서 검증은 DTO `@AssertTrue`가 별도 담당

**중복 예약 검증 상태 리스트**: 현재 `PENDING, RESERVED`.
→ **결제 연동 시 결정 필요**: `PENDING_PAYMENT`를 포함할지 (포함 시 미결제 이탈 예약이 자리를 점유하므로 자동 만료 정책과 세트로 결정)

**낙관적 락**: `reservations.version BIGINT` (Flyway V11), `@Version` 필드.
`GlobalExceptionHandler`에 `ObjectOptimisticLockingFailureException` 핸들러 구현 완료 (409).
서비스 내부 try-catch로는 잡히지 않음 — 커밋 시점에 발생하므로 반드시 핸들러 레벨에서 처리.

## 4. 레이어 규칙 (확정 컨벤션)

- **userId는 절대 request body/URL로 받지 않는다** — 인증 토큰(SecurityContext)에서 추출해 컨트롤러가 서비스에 파라미터로 전달. IDOR 방지.
- **campId는 request body로 받는다** — 클라이언트 선택 정보. 단 `@NotNull` 검증 + Camp 존재 검증(Camp 도메인 완성 후).
- **totalPrice는 클라이언트를 신뢰하지 않는다** — 결제 연동 시 서버가 날짜×단가로 재계산·검증 필수. 실결제 금액이므로 조작 방어가 결제 전 필수 사항.
- DTO는 형식 검증(`@Valid`), 서비스는 DB 상태 대조 비즈니스 검증. 날짜 순서 검증은 DTO `@AssertTrue`로 일원화.
- 응답 DTO는 대상별 분리: `CustomerReservationResponse` / `OwnerReservationResponse` (사업자용은 customerName/Phone/specialRequest가 핵심, 고객용은 rejectReason/cancelDate 포함).
- 목록 조회는 `Page` + `@PageableDefault(sort = "createdAt", direction = DESC)` + `@ParameterObject`(Swagger 렌더링).
- 본인 예약 검증(취소·조회 시 `reservation.getUserId().equals(currentUserId)`), 사업자 소유권 검증(`camp.getOwnerId().equals(userId)` → `ACCESS_DENIED`, 403)은 Camp 도메인 완성 후 활성화 예정 TODO.

## 5. 결제 연동 시 구현·결정 필요 사항

**구현 필요**
- [ ] `ReservationStatus.PENDING_PAYMENT` 추가 + Flyway 마이그레이션(상태는 VARCHAR라 enum 추가만으로 충분한지 확인)
- [ ] 결제 도메인: PG 연동, 결제 기록 엔티티, 웹훅/콜백 엔드포인트
- [ ] 결제 확인 → PENDING 전이 로직
- [ ] 거절/취소 시 환불 연동 (기존 `cancel()`, `reject()` 흐름에 연결)
- [ ] 결제 멱등성 처리 (더블클릭/재시도로 이중 결제 방지, 멱등키)
- [ ] 서버 측 totalPrice 재계산 검증

**기획 확정 필요 (팀 논의 안건)**
- [ ] 미결제(PENDING_PAYMENT) 예약 자동 만료 시간
- [ ] PENDING_PAYMENT의 중복 예약 검증 포함 여부
- [ ] RESERVED 상태 취소 시 환불 정책 (전액 / 체크인 N일 전 기준 차등)
- [ ] 결제 콜백 유실 대비 대사(reconciliation) 방식 (초기엔 관리자 수동 확인 허용 여부)

## 6. 에러 코드 (Reservation)

```
INVALID_RESERVATION_PERIOD   (400, R001) 체크아웃은 체크인 이후
RESERVATION_NOT_FOUND        (404, R002) 예약 없음
ALREADY_CANCELED_OR_REJECTED (400, R003) 이미 취소/거절된 예약
RESERVATION_NOT_PENDING      (400, R004) 대기 중인 예약만 수락/거절 가능
DUPLICATE_RESERVATION        (409, R005) 본인 활성 예약과 기간 겹침
RESERVATION_PERIOD_CONFLICT  (409, R006) 확정 예약과 기간 겹침 (승인 API에서 사용 예정)
CONCURRENT_MODIFICATION      (409)       낙관적 락 충돌
```

## 7. 운영 주의사항

- Flyway 마이그레이션 번호는 **PR 머지 직전 develop 기준 최신+1로 확정** (V10 중복 충돌 재발 방지). 현재 예약 version 컬럼은 V11.
- `ddl-auto: validate` — 엔티티 변경은 반드시 Flyway 마이그레이션과 세트.
- 소프트 딜리트 구조이므로 reservations에 DB 유니크 제약을 걸지 않는다 (CANCELLED 행이 재예약을 막게 됨). 정합성은 3층 방어(3절)로 보장.
