# BaseCamp Backend — CLAUDE.md

> Claude가 매번 먼저 읽는 프로젝트 설명서. 팀 공유용.

## 프로젝트 개요

캠핑장 예약 플랫폼(BaseCamp)의 백엔드. REST API 서버.

## 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 / JDK | Java 21 |
| 프레임워크 | Spring Boot 3.3.x |
| 빌드 | Gradle (Groovy DSL) |
| ORM | Spring Data JPA |
| DB | MySQL 8.x |
| 마이그레이션 | Flyway (`src/main/resources/db/migration`) |
| 인증 | Spring Security + JWT (jjwt) |
| 소셜 로그인 | OAuth2 Client (Kakao / Google / Naver) |
| API 문서 | springdoc-openapi (Swagger UI) |
| 기타 | Lombok |

## 패키지 구조

```
com.basecamp.backend
├── common/              # 공통 모듈
│   ├── config/          # SwaggerConfig 등 설정
│   └── exception/       # BusinessException, ErrorCode, GlobalExceptionHandler
└── domain/              # 도메인별 패키지 (도메인당 아래 레이어 반복)
    ├── auth/            # 인증/인가, 소셜 로그인
    ├── camp/            # 캠핑장
    ├── comment/         # 댓글
    ├── map/             # 지도/위치
    ├── member/          # 회원
    ├── notification/    # 알림
    ├── payment/         # 결제
    ├── post/            # 게시글
    ├── reservation/     # 예약
    └── review/          # 리뷰
```

각 도메인 내부 레이어:

```
domain/<name>/
├── controller/          # REST 컨트롤러 (@RestController)
├── dto/
│   ├── request/         # 요청 DTO
│   └── response/        # 응답 DTO
├── entity/              # JPA 엔티티
├── repository/          # Spring Data JPA 리포지토리
└── service/             # 비즈니스 로직
```

## 주요 명령어

```bash
./gradlew build          # 빌드
./gradlew test           # 테스트
./gradlew bootRun        # 로컬 실행
./gradlew flywayMigrate  # (설정 시) 마이그레이션
```

Swagger UI: 실행 후 `http://localhost:8080/swagger-ui.html`

## 코딩 규칙

- 계층 흐름은 **Controller → Service → Repository** 단방향. 컨트롤러에 비즈니스 로직 금지.
- 요청/응답은 엔티티가 아닌 **DTO**로 주고받는다.
- 예외는 `BusinessException` + `ErrorCode`로 던지고, `GlobalExceptionHandler`가 응답으로 변환한다.
- 스키마 변경은 반드시 새 **Flyway 마이그레이션 파일**(`V{n}__설명.sql`)로. 기존 파일 수정 금지.
- 상세 규칙은 `.claude/rules/` 참고 (테스트 → `testing.md`, API 설계 → `api-design.md`).

## 하지 말 것

- 시크릿(`application-secret.yml`, `.env`) 커밋 금지.
- 이미 적용된 마이그레이션 파일(`V1`~`V5`) 내용 변경 금지 — 항상 새 버전 추가.
