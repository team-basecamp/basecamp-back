# security 패키지 구조화 계획

> `com.basecamp.backend.security` 의 14개 파일이 한 패키지에 평면적으로 모여 있어, 책임별 하위 패키지로 정리한다.
> **아직 코드는 바꾸지 않음 — 계획만.**

## 현재 상태 (flat, 14 files)

```
security/
├── SecurityConfig.java
├── JwtProperties.java  CorsProperties.java  CookieProperties.java
├── JwtTokenProvider.java  JwtAuthenticationFilter.java
├── JwtAuthenticationEntryPoint.java  JwtAccessDeniedHandler.java  SecurityResponseWriter.java
├── OAuthStateProvider.java
├── TokenBlacklistCache.java  UserRevocationCache.java
├── CookieUtil.java  TempTokenPrinter.java
```

## 목표 구조 (책임별 6개 하위 패키지)

```
security/
├── SecurityConfig.java          # 루트 유지 — 전체를 조립하는 진입점
├── config/                      # 설정값 프로퍼티
│   ├── JwtProperties.java
│   ├── CorsProperties.java
│   └── CookieProperties.java
├── jwt/                         # 토큰 발급·파싱 + 인증 필터
│   ├── JwtTokenProvider.java
│   └── JwtAuthenticationFilter.java
├── handler/                     # 필터 단계 인증/인가 실패 응답 (셋을 함께 둬 pkg-private 유지)
│   ├── JwtAuthenticationEntryPoint.java
│   ├── JwtAccessDeniedHandler.java
│   └── SecurityResponseWriter.java
├── oauth/
│   └── OAuthStateProvider.java
├── cache/                       # Redis 무효화 캐시
│   ├── TokenBlacklistCache.java
│   └── UserRevocationCache.java
└── support/                     # 보조 유틸
    ├── CookieUtil.java
    └── TempTokenPrinter.java
```

## 파일 이동 매핑

| 파일 | 이동 위치 | 역할 |
|---|---|---|
| `SecurityConfig` | `security/` (그대로) | 필터체인·CORS·인가규칙 조립점 |
| `JwtProperties` | `config/` | `@ConfigurationProperties(jwt)` |
| `CorsProperties` | `config/` | `@ConfigurationProperties(cors)` |
| `CookieProperties` | `config/` | `@ConfigurationProperties(cookie.refresh)` |
| `JwtTokenProvider` | `jwt/` | JWT 발급·파싱 |
| `JwtAuthenticationFilter` | `jwt/` | 요청당 인증 필터 |
| `JwtAuthenticationEntryPoint` | `handler/` | 401 응답 |
| `JwtAccessDeniedHandler` | `handler/` | 403 응답 |
| `SecurityResponseWriter` | `handler/` | 실패 응답 봉투 직렬화 (pkg-private) |
| `OAuthStateProvider` | `oauth/` | OAuth state(CSRF) 생성·검증 |
| `TokenBlacklistCache` | `cache/` | jti 블랙리스트 캐시 |
| `UserRevocationCache` | `cache/` | 회원 단위 토큰 무효화 캐시 |
| `CookieUtil` | `support/` | refresh 쿠키 조립/삭제 |
| `TempTokenPrinter` | `support/` | 부팅 시 임시 토큰 출력 (개발용, **이동만** — 동작 손대지 않음) |

## 주의사항

1. **`SecurityResponseWriter` 는 package-private** `final class` 이고 `JwtAuthenticationEntryPoint`·`JwtAccessDeniedHandler` 만 사용한다.
   → 셋을 반드시 같은 `handler/` 패키지에 함께 둬야 public 으로 열지 않고 그대로 유지된다.
2. **`@EnableConfigurationProperties({JwtProperties, CorsProperties, CookieProperties})`** 는 클래스 참조 방식(`SecurityConfig`)이라
   패키지를 옮겨도 `import` 만 고치면 된다. 스캔 경로 문제 없음.
3. **컴포넌트 스캔**: 메인 앱이 `com.basecamp.backend` 루트에 있어 모든 하위 패키지를 스캔한다. `@Component`/`@Configuration` 이동은 자유롭다.
4. **외부 import 수정 (~13곳)**: 아래에서 security 클래스를 참조한다. 이동 시 `import` 경로를 전부 갱신해야 한다.
   - `UserRevocationCache` → `AdminUserService`, `AdminCampOwnerService` (+테스트 2)
   - `JwtTokenProvider` → `AuthService`, `AuthTransactionService` (+테스트 2)
   - `TokenBlacklistCache` → `AuthTransactionService` (+테스트 1)
   - `OAuthStateProvider` → `AuthService` (+테스트 1)
   - `CookieUtil` → `AuthController` (+테스트 1)
   - `JwtAuthenticationFilter` → `AuthController`
   - `JwtProperties` → 테스트 2
   - > `common.model.AuthUser`(인증 principal)는 security 패키지가 아니라 **이동 대상 아님**.
5. **패키지 간 내부 import 추가**: 지금은 같은 패키지라 import 없이 서로 참조하지만, 하위 패키지로 쪼개지면
   `SecurityConfig` 등이 각 클래스를 import 해야 한다 (전부 기계적).

## 실행 순서 (제안)

1. 하위 패키지 6개 디렉터리 생성 후 파일 이동 + 각 파일 `package` 선언 변경.
2. security 내부 상호 참조 import 추가 (`SecurityConfig`, `JwtAuthenticationFilter`, 두 handler 등).
3. 외부 참조처(auth·admin 서비스/컨트롤러 + 테스트) `import` 경로 일괄 갱신.
4. `./gradlew build` 로 컴파일·전체 테스트 검증 (특히 `@EnableConfigurationProperties`, 필터체인, 401/403 핸들러 슬라이스 테스트).

## 열린 결정

- `TempTokenPrinter` 는 이번엔 **이동만** 하기로 함. 운영 노출 방지(`@Profile("local")`)나 제거는 별도 이슈로 남긴다.
