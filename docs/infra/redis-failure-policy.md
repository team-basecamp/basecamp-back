# Redis 장애 시 폴백 정책

> 한 줄 요약: **조회는 fail-open(통과), 쓰기는 예외 전파(DB 트랜잭션 중단).** Redis 는 조회 캐시일 뿐이고, 진실의 원천(source of truth)은 항상 MySQL 이다.
>
> ⚠️ **Redis 쓰기는 DB 트랜잭션에 참여하지 않는다.** 두 캐시는 `StringRedisTemplate` 으로 Redis 에 직접 쓰므로, 예외를 전파해 DB 트랜잭션을 중단시킬 수는 있어도 **이미 성공한 Redis 쓰기가 되돌아가지는 않는다.** 그래서 쓰기 순서를 "남는 찌꺼기가 안전한 방향"이 되도록 고정해 뒀다(§4).

## 1. Redis 에 무엇을 두는가

인증/인가 경로의 **조회 캐시** 두 개만 Redis(`StringRedisTemplate`)를 쓴다. 둘 다 `security/cache` 패키지에 있다.

| 캐시 | 클래스 | 키 | 진실의 원천(MySQL) | TTL |
|------|--------|----|--------------------|-----|
| 토큰 폐기(jti) | `TokenBlacklistCache` | `blacklist:{jti}` | `token_blacklist` | 토큰 잔여 수명 |
| 회원 무효화(강제 로그아웃/제재) | `UserRevocationCache` | `revoke:user:{userId}` | `users.status` | access 토큰 수명(기본 30분) |

access 토큰 검증은 매 API 요청마다 일어나므로(`JwtAuthenticationFilter`), 매번 DB 를 때리지 않으려고 조회를 Redis 로 캐싱한다.

## 2. 연산별 정책

| 연산 | 정책 | 동작 | 이유 |
|------|------|------|------|
| **조회** `isBlacklisted` / `isRevoked` | **fail-open** | `WARN` 로그 후 `false` 반환 → 요청 통과 | Redis 를 인증 전체의 단일 장애점(SPOF)으로 만들지 않는다 |
| **쓰기** `blacklist` / `revoke` / `clear` | **예외 전파** | 예외를 그대로 던져 호출 **DB 트랜잭션을 중단**(Redis 쓰기 자체는 롤백되지 않음 → §4) | 조용히 넘기면 "로그아웃/제재했는데 토큰이 살아 있는" 상태가 된다 |

## 3. 왜 조회는 fail-open 이어도 안전한가

조회가 실패해 폐기/제재된 토큰이 통과하더라도 피해는 제한적이다.

- **노출 창(window)** 은 *이미 발급된* access 토큰의 **잔여 수명(기본 30분)** 으로 한정된다.
- **재로그인·토큰 재발급은 여전히 MySQL 로 막힌다** — 소셜 로그인과 리프레시가 모두 `users.status = BLACKLISTED` 를 확인하므로, 제재 중에는 새 토큰이 애초에 발급되지 않는다.
- 즉 최악의 경우도 "토큰 무효화 기능 도입 이전(= access 토큰은 만료 전까지 유효)"으로 **일시 퇴화**할 뿐, 제재를 영구히 우회하는 것은 불가능하다.

availability(가용성) 우선: Redis 한 대가 죽었다고 전체 로그인/API 를 막는 것보다, 30분 이내의 좁은 노출 창을 감수하는 편이 낫다는 판단이다.

## 4. 왜 쓰기는 예외를 전파하는가

`blacklist`·`revoke` 는 **권한 축소**(로그아웃/제재) 작업이다. 실패를 삼키면 "방금 죽여야 할 토큰이 살아남는" 보안 구멍이 생긴다. 그래서 예외를 전파해 호출 트랜잭션과 함께 롤백시킨다.

- 호출처: `AuthTransactionService`(로그아웃/토큰 회전), `AdminUserService`(제재), `AdminCampOwnerService`(업체 승격 시 구 토큰 무효화).
- 예) 승격 트랜잭션에서 `UserRevocationCache.revoke()` 가 실패하면 예외가 전파되어 승격 자체가 롤백된다 — "승격됐는데 구 토큰(CUSTOMER)이 살아 있는" 어중간한 상태를 막는다.

## 5. 기동 / 연결 동작

- Lettuce 는 **지연 연결(lazy)** 이라 **Redis 가 없어도 애플리케이션은 정상 기동**된다(`application.yml` 의 `spring.data.redis` 주석 참고).
- 연결은 첫 조회 시점에 시도되고, 실패하면 위 fail-open 정책이 적용된다.
- 따라서 **로컬 개발이나 CI 에서 Redis 가 없어도** 앱 실행과 인증 경로(조회)는 동작한다. 다만 쓰기 경로(로그아웃/제재)는 Redis 가 있어야 성공한다.

## 6. 테스트 · CI 함의

- 캐시 단위 테스트(`TokenBlacklistCacheTest`, `UserRevocationCacheTest`)는 `StringRedisTemplate` 을 **Mockito 로 목킹**한다 → 실제 Redis 불필요.
- fail-open 테스트(`isRevoked_Redis조회실패_failopen으로_false를반환한다` 등)는 조회가 예외를 던지는 상황을 시뮬레이션한다. 이때 `log.warn("... 이 요청은 통과시킨다 ...", e)` 가 **스택트레이스와 함께 `STANDARD_OUT` 에 찍히는데, 이것은 정상 로그 출력이지 테스트 실패가 아니다.**
- GitHub Actions CI 에는 Redis 서비스 컨테이너가 없고, **필요도 없다**(단위 테스트는 목킹, 통합 테스트는 Redis 를 쓰지 않음).

## 관련 코드

- `security/cache/TokenBlacklistCache.java`
- `security/cache/UserRevocationCache.java`
- `security/jwt/JwtAuthenticationFilter.java` (매 요청 조회 경로)
- `src/main/resources/application.yml` (`spring.data.redis`)
