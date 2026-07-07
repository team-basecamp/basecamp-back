# BaseCamp Backend

캠핑장 예약 플랫폼 백엔드

## 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 / JDK | Java 21 |
| 프레임워크 | Spring Boot 3.3.x |
| 빌드 도구 | Gradle (Groovy DSL) |
| ORM | Spring Data JPA |
| DB | MySQL |
| DB 마이그레이션 | Flyway |
| 인증 | Spring Security + JWT (jjwt) |
| 소셜 로그인 | Spring OAuth2 Client (Kakao / Google / Naver) |
| API 문서화 | springdoc-openapi (Swagger UI) |
| 기타 | Lombok |

## 환경설정 방법

### 요구사항

- JDK 21
- MySQL 8.x
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

### 3. 환경변수 설정

`src/main/resources/application.yml`은 아래 환경변수를 참조합니다. 로컬 실행 시 IntelliJ Run Configuration의 Environment variables 또는 시스템 환경변수로 설정하세요. 값을 설정하지 않으면 `:` 뒤에 명시된 기본값(로컬 개발용)이 사용됩니다.

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `DB_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/basecamp?...` |
| `DB_USERNAME` | DB 사용자명 | `root` |
| `DB_PASSWORD` | DB 비밀번호 | `password` |
| `SERVER_PORT` | 서버 포트 | `8080` |
| `JWT_SECRET` | JWT 서명 키 | 로컬 개발용 임시 값 |
| `JWT_ACCESS_EXPIRATION` | Access Token 만료시간(ms) | `1800000` (30분) |
| `JWT_REFRESH_EXPIRATION` | Refresh Token 만료시간(ms) | `1209600000` (14일) |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | 카카오 로그인 키 | - |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | 구글 로그인 키 | `dummy-google-client-id` 등 |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` | 네이버 로그인 키 | `dummy-naver-client-id` 등 |

> 소셜 로그인 키는 값이 없으면 OAuth2 자동 설정이 실패해 서버 자체가 기동되지 않으므로, 실제 키가 없는 로컬/CI 환경에서도 앱이 뜰 수 있도록 더미 값을 기본값으로 설정해두었습니다. 실제 소셜 로그인을 테스트하려면 위 환경변수에 발급받은 키를 설정하세요.

> 실제 키 값이나 운영 DB 정보는 절대 커밋하지 마세요. 필요 시 `application-secret.yml`(gitignore 처리됨)을 만들어 관리하는 것을 권장합니다.

### 4. 빌드 및 실행

```bash
./gradlew build
./gradlew bootRun
```

또는 IntelliJ에서 `BasecampApplication`을 직접 실행합니다.

### 5. Swagger 접속

서버 실행 후 아래 주소에서 API 문서를 확인할 수 있습니다.

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI Spec (JSON): http://localhost:8080/v3/api-docs

## 프로젝트 구조

```
src/main/java/com/basecamp/backend
├── BasecampApplication.java
├── common/                     # 도메인에 속하지 않는 공통 모듈
│   ├── config/                 # Swagger, Security 등 전역 설정
│   └── exception/               # 전역 예외 처리 (GlobalExceptionHandler, ErrorCode ...)
└── domain/                     # 도메인별 패키지 (ERD 기준)
    ├── auth/                   # 소셜 로그인, 토큰 발급/재발급/블랙리스트
    ├── user/                   # 회원 정보, 탈퇴, 관리자 회원 관리
    ├── map/                    # 지도 / 고캠핑 API 연동
    ├── camp/                   # 캠핑장, 찜(위시리스트)
    ├── post/                   # 게시글, 신고, 블라인드
    ├── comment/                # 댓글
    ├── reservation/            # 예약
    ├── payment/                # 결제 (Mock)
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

## 데이터베이스 마이그레이션

DB 스키마는 Flyway로 관리합니다. 마이그레이션 스크립트는 `src/main/resources/db/migration` 아래에 `V{순번}__{설명}.sql` 형식으로 추가합니다. (예: `V1__init.sql`)

## CI (GitHub Actions)

이 프로젝트는 별도 서버 배포 없이 로컬 실행만 하지만, `dev` 브랜치 머지 전 빌드 오류를 걸러내기 위해 GitHub Actions로 CI를 구성했습니다. (`.github/workflows/ci.yml`)

- 트리거: `dev` 브랜치로의 Pull Request, `dev` 브랜치로의 Push
- 실행 내용: JDK 21 세팅 → MySQL 8 서비스 컨테이너 기동 → `./gradlew build` (컴파일 + 테스트 + Flyway 마이그레이션 검증)
- 실패 시 `build/reports/tests/test` 리포트가 Actions 아티팩트로 업로드됩니다.
