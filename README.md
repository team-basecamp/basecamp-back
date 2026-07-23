# BaseCamp Backend

캠핑장 예약 플랫폼 백엔드

## 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 / JDK | Java 21 |
| 프레임워크 | Spring Boot 3.3.x |
| 빌드 도구 | Gradle (Groovy DSL) |
| ORM | Spring Data JPA |
| DB | MySQL 8.x |
| DB 마이그레이션 | Flyway |
| 조회 캐시 | Redis 7.x (토큰 블랙리스트 · 회원 무효화 · 날씨 캐시) |
| 오브젝트 스토리지 | MinIO (프로필 / 게시글 / 리뷰 / 캠핑장 이미지) |
| 인증 | Spring Security + JWT (jjwt) |
| 소셜 로그인 | Spring OAuth2 Client (Kakao / Google / Naver) |
| 결제 | PortOne(포트원) V2 — 카카오페이 / 토스페이 / 이니시스 |
| 외부 API | 고캠핑(GoCamping) · 카카오 로컬(지오코딩) · OpenWeatherMap |
| API 문서화 | springdoc-openapi (Swagger UI) |
| 코드 포맷 | Spotless (Google Java Format) |
| 기타 | Lombok |

## 환경설정 방법

### 요구사항

- JDK 21
- MySQL 8.x
- (선택) Docker — Redis / MinIO를 로컬에서 한 번에 띄우는 용도 (`docker compose up -d`)
  - **Redis**: 토큰 블랙리스트 조회 캐시. 없어도 앱은 기동되며, 로그아웃된 access token이 만료 전까지 유효한 상태로 퇴화합니다.
  - **MinIO**: 이미지 업로드/조회 저장소. 이미지 기능을 쓰려면 필요합니다.
- (선택) IntelliJ IDEA

### 1. 저장소 클론

```bash
git clone https://github.com/team-basecamp/basecamp-back.git
cd basecamp-back
```

### 2. MySQL 데이터베이스 생성

```sql
CREATE DATABASE basecamp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. Redis / MinIO 실행 (선택)

프로젝트 루트의 `docker-compose.yml`은 **Redis**, **MinIO**, 그리고 버킷을 자동 생성하는 **minio-init** 세 서비스를 정의합니다.

```bash
docker compose up -d
```

- **Redis** — 로그아웃·탈퇴한 access token을 만료 전에 거부하기 위한 조회 캐시입니다. 인증 필터가 매 요청마다 조회하며, 영속 기록(MySQL `token_blacklist`)을 직접 조회하지 않기 위한 것입니다.
  > Redis가 없거나 조회에 실패하면 필터는 **fail-open**(경고 로그 후 통과)합니다. Redis를 단일 장애점으로 만들지 않기 위한 선택이며, 이때 동작은 "access token은 만료(기본 30분)까지 유효"로 퇴화합니다. Refresh token 재발급 경로는 Redis를 쓰지도 읽지도 않으므로(재사용 탐지는 MySQL 담당) Redis 상태와 무관합니다. 자세한 폴백 정책은 [docs/decisions/redis-failure-policy.md](docs/decisions/redis-failure-policy.md) 참고.
- **MinIO** — 프로필/게시글/리뷰/캠핑장 이미지를 저장하는 오브젝트 스토리지입니다. `minio-init`이 기동 시 `basecamp` 버킷을 anonymous-download 정책으로 생성합니다. 콘솔은 http://localhost:9001 (기본 계정 `basecamp` / `basecamp123`).

> Docker가 처음이라면 [docs/guides/docker-setting.md](docs/guides/docker-setting.md), 이미지 저장 흐름은 [docs/guides/minio-setting.md](docs/guides/minio-setting.md)를 참고하세요.

### 4. 환경변수 설정

`src/main/resources/application.yml`은 아래 환경변수를 참조합니다. 로컬 실행 시 IntelliJ Run Configuration의 Environment variables 또는 시스템 환경변수로 설정하세요. 값을 설정하지 않으면 `:` 뒤 기본값(로컬 개발용)이 사용됩니다.

**DB / 서버**

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `DB_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/basecamp?...` |
| `DB_USERNAME` | DB 사용자명 | `root` |
| `DB_PASSWORD` | DB 비밀번호 | `password` |
| `SERVER_PORT` | 서버 포트 | `8080` |

**Redis (조회 캐시)**

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `REDIS_HOST` | Redis 호스트 | `localhost` |
| `REDIS_PORT` | Redis 포트 | `6379` |

**JWT / 인증**

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `JWT_SECRET` | JWT 서명 키 | 로컬 개발용 임시 값 |
| `JWT_ACCESS_EXPIRATION` | Access Token 만료시간(ms) | `1800000` (30분) |
| `JWT_REFRESH_EXPIRATION` | Refresh Token 만료시간(ms) | `1209600000` (14일) |

**쿠키 / CORS**

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `CORS_ALLOWED_ORIGINS` | credentials 허용 CORS 오리진 화이트리스트 (콤마 구분) | `http://localhost:3000` |
| `COOKIE_REFRESH_NAME` | Refresh Token 쿠키 이름 | `refreshToken` |
| `COOKIE_REFRESH_PATH` | Refresh Token 쿠키 경로 | `/api/v1/auth` |
| `COOKIE_REFRESH_SAMESITE` | Refresh Token 쿠키 SameSite 속성 | `Lax` |
| `COOKIE_REFRESH_SECURE` | Refresh Token 쿠키 Secure 플래그 | `false` |

**소셜 로그인 (OAuth2)**

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | 카카오 로그인 키 | 더미 값 |
| `KAKAO_REDIRECT_URI` | 카카오 redirect URI | `http://localhost:8080/login/oauth2/code/kakao` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | 구글 로그인 키 | 더미 값 |
| `GOOGLE_REDIRECT_URI` | 구글 redirect URI | `http://localhost:3000/oauth/google/callback` |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` | 네이버 로그인 키 | 더미 값 |
| `NAVER_REDIRECT_URI` | 네이버 redirect URI | `http://localhost:8080/login/oauth2/code/naver` |

**결제 (PortOne V2)**

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `PORTONE_STORE_ID` | 포트원 상점 ID | 테스트 모드 값 |
| `PORTONE_CHANNEL_KEY_INICIS` | 이니시스 채널 키 | 테스트 모드 값 |
| `PORTONE_CHANNEL_KEY_KAKAOPAY` | 카카오페이 채널 키 | 테스트 모드 값 |
| `PORTONE_CHANNEL_KEY_TOSSPAY` | 토스페이 채널 키 | 테스트 모드 값 |
| `PORTONE_API_SECRET` | 포트원 REST API 시크릿 | (빈 값) |
| `PORTONE_WEBHOOK_SECRET` | 웹훅 서명 검증 시크릿 | (빈 값) |
| `PORTONE_API_BASE_URL` | 포트원 API 베이스 URL | `https://api.portone.io` |
| `PAYMENT_WAITING_EXPIRY` | 결제 대기 만료(분) | `30` |

**이미지 저장소 (MinIO) / 업로드**

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `MINIO_ENDPOINT` | 서버 → MinIO 접속 주소 | `http://localhost:9000` |
| `MINIO_PUBLIC_ENDPOINT` | 클라이언트에 노출할 주소(DB 저장 URL 접두어) | `http://localhost:9000` |
| `MINIO_ROOT_USER` | MinIO 액세스 키 | `basecamp` |
| `MINIO_ROOT_PASSWORD` | MinIO 시크릿 키 | `basecamp123` |
| `MINIO_BUCKET` | 버킷 이름 | `basecamp` |
| `IMAGE_MAX_COUNT` | 한 요청당 첨부 이미지 최대 개수 | `10` |
| `FILE_MAX_FILE_SIZE` | 파일 1개 업로드 상한 | `5MB` |
| `FILE_MAX_REQUEST_SIZE` | 요청 전체 업로드 상한 | `50MB` |

**외부 API**

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `GOCAMPING_API_KEY` | 고캠핑(공공데이터) API 키 | 더미 값 |
| `KAKAO_REST_API_KEY` | 카카오 로컬(주소 지오코딩) REST API 키 | 더미 값 |
| `OPENWEATHER_API_KEY` | OpenWeatherMap API 키 | 더미 값 |
| `OPENWEATHER_REGION_TTL` | 지역 날씨 캐시 TTL(분) | `30` |

**캠핑장 초기 데이터**

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `CAMP_DATA_INIT_ENABLED` | 기동 시 고캠핑 API 자동 동기화 스위치 | `false` |
| `CAMP_DEFAULT_PRICE_MIN` / `MAX` / `UNIT` | 신규 캠핑장 임의 가격 부여 범위(고캠핑이 가격 미제공) | `50000` / `100000` / `1000` |

> Refresh Token은 HttpOnly 쿠키로 발급됩니다. 로컬은 same-site라 `Lax`/`Secure=false`로 충분하지만, 배포 시 프론트·백 도메인이 다르면(cross-site) `COOKIE_REFRESH_SAMESITE=None` + `COOKIE_REFRESH_SECURE=true`로 설정해야 합니다. (`SameSite=None`이면 `Secure=true` 필수)

> 소셜 로그인 키·외부 API 키는 값이 없으면 자동 설정이 실패하거나 연동이 동작하지 않으므로, 실제 키가 없는 로컬/CI 환경에서도 앱이 뜰 수 있도록 더미 값을 기본값으로 두었습니다. 실제 기능을 테스트하려면 위 환경변수에 발급받은 키를 설정하세요.

> `CAMP_DATA_INIT_ENABLED`를 지우면 `@ConditionalOnProperty(matchIfMissing = true)` 때문에 "켜짐"으로 되돌아갑니다. 매 기동마다 외부 API를 호출하지 않으려면 `false`를 명시해 두세요. 데이터를 채울 때는 관리자 권한으로 `POST /api/v1/camps/fetch`를 호출합니다.

> 실제 키 값이나 운영 DB 정보는 절대 커밋하지 마세요. 필요 시 `application-secret.yml`(gitignore 처리됨)을 만들어 관리하는 것을 권장합니다.

### 5. 빌드 및 실행

```bash
./gradlew build          # 컴파일 + 테스트 + Spotless 포맷 검증
./gradlew bootRun        # 로컬 실행
```

또는 IntelliJ에서 `BasecampApplication`을 직접 실행합니다.

> 코드 포맷은 Spotless(Google Java Format)로 관리합니다. **커밋 전 `./gradlew spotlessApply`로 포맷을 맞추세요.** `enforceCheck`가 켜져 있어 포맷이 어긋나면 `./gradlew build`(→ `spotlessCheck`)가 실패합니다. 자세한 내용은 [docs/guides/spotless-guide.md](docs/guides/spotless-guide.md) 참고.

### 6. Swagger 접속

서버 실행 후 아래 주소에서 API 문서를 확인할 수 있습니다.

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI Spec (JSON): http://localhost:8080/v3/api-docs

## 프로젝트 구조

```
src/main/java/com/basecamp/backend
├── BasecampApplication.java
├── common/                     # 도메인에 속하지 않는 공통 모듈
│   ├── config/                 # Swagger 등 전역 설정
│   ├── enums/                  # 공통 열거형
│   ├── exception/              # 전역 예외 처리 (GlobalExceptionHandler, ErrorCode, BusinessException ...)
│   ├── model/                  # 인증 주체(AuthUser) 등 공통 모델
│   ├── response/               # 공통 응답 봉투 (ApiResponse, ApiResponseWrapAdvice)
│   └── storage/                # 오브젝트 스토리지 추상화 + MinIO 구현 (이미지 저장)
├── security/                   # Spring Security 설정 및 인증 인프라
│   ├── SecurityConfig.java
│   ├── cache/                  # TokenBlacklistCache, UserRevocationCache (Redis)
│   ├── config/                 # Jwt/Cors/Cookie Properties
│   ├── handler/                # 인증 실패(401)/인가 실패(403) 핸들러, 응답 직렬화
│   ├── jwt/                    # JWT 발급·검증 필터
│   ├── oauth/                  # OAuth state 관리
│   └── support/                # CookieUtil 등
└── domain/                     # 도메인별 패키지
    ├── auth/                   # 소셜 로그인, 토큰 발급/재발급/블랙리스트
    ├── user/                   # 회원 정보, 탈퇴
    ├── admin/                  # 관리자 (회원·캠핑장주·신고 관리)
    ├── campowner/              # 캠핑업체(CAMP_OWNER) 권한 승격 신청
    ├── camp/                   # 캠핑장, 찜(위시리스트)
    ├── map/                    # 지도 / 고캠핑 API 연동
    ├── weather/                # 캠핑장 좌표 기반 날씨 (OpenWeatherMap)
    ├── post/                   # 게시글, 신고, 블라인드
    ├── comment/                # 댓글
    ├── reservation/            # 예약
    ├── payment/                # 결제 (PortOne 연동 + 웹훅)
    ├── review/                 # 리뷰
    └── notification/           # 알림
```

각 도메인 패키지는 아래와 같이 계층별로 구성됩니다.

```
domain/{도메인명}/
├── controller/
├── service/
├── repository/
├── entity/
└── dto/
    ├── request/
    └── response/
```

## 문서 (docs/)

문서는 성격별로 나눠 관리합니다.

```
docs/
├── guides/       # 따라 하는 사용법 (온보딩)
│   ├── auth-guide.md              # 로그인이 필요한 API 만들기
│   ├── docker-setting.md          # Docker / Redis 로컬 세팅
│   ├── minio-setting.md           # MinIO 이미지 저장소 세팅
│   └── spotless-guide.md          # Spotless 코드 포맷
├── reference/    # 명세 · 스키마 레퍼런스
│   ├── api-description.md         # 전체 REST API 명세
│   ├── erd-v3.png                 # ERD
│   └── camp-owner-applications-and-fk-policy.md  # 승격 신청 & FK 삭제 정책
├── decisions/    # 설계 결정 · 정책 (ADR 성격)
│   ├── camp-owner-promotion.md    # CAMP_OWNER 권한 부여 설계
│   ├── redis-failure-policy.md    # Redis 장애 시 폴백 정책
│   └── security-package-restructure.md  # security 패키지 구조화
└── dev-log/
    └── back-log-summary.md        # 개발 로그 요약
```

## 데이터베이스 마이그레이션

DB 스키마는 Flyway로 관리합니다. 마이그레이션 스크립트는 `src/main/resources/db/migration` 아래에 `V{순번}__{설명}.sql` 형식으로 추가합니다. (예: `V1__init.sql`)

- 이미 적용된 마이그레이션 파일은 수정하지 않고, 스키마 변경은 **항상 새 버전 파일**을 추가합니다.
- `ddl-auto: validate`이므로 엔티티 매핑과 실제 스키마가 어긋나면 기동이 실패합니다.

## CI (GitHub Actions)

이 프로젝트는 별도 서버 배포 없이 로컬 실행만 하지만, `dev` 브랜치 머지 전 빌드 오류를 걸러내기 위해 GitHub Actions로 CI를 구성했습니다. (`.github/workflows/ci.yml`)

- 트리거: `dev` 브랜치로의 Pull Request, `dev` 브랜치로의 Push
- 실행 내용: JDK 21 세팅 → MySQL 8 서비스 컨테이너 기동 → `./gradlew build` (컴파일 + 테스트 + Flyway 마이그레이션 검증 + Spotless 포맷 검증)
- 실패 시 `build/reports/tests/test` 리포트가 Actions 아티팩트로 업로드됩니다.
