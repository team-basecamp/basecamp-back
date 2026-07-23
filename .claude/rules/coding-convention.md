---
description: 백엔드 Java 코드 전반에 적용되는 코딩 컨벤션 및 포맷 규칙
globs:
  - "src/main/java/com/basecamp/backend/**"
---

# 코딩 컨벤션

## 계층 책임
- **Controller 는 얇게 유지한다.** 요청 바인딩 · 검증(`@Valid`) · Service 호출 · 응답 반환까지만 담당한다.
- 비즈니스 로직은 전부 **Service** 에 둔다. 분기, 계산, 상태 판단이 컨트롤러에 들어가면 Service 로 내린다.
- 계층 흐름은 `Controller → Service → Repository` 단방향을 유지한다.

## 하드코딩 금지
- 설정값(경로, 엔드포인트, 키, 제한 수치, 만료 시간 등)을 코드에 직접 박지 않는다.
- `application.yml` 에 두고 `@ConfigurationProperties` 또는 `@Value` 로 주입받는다. 타입 안전성과 검증(`@NotBlank` 등)이 가능한 `@ConfigurationProperties` 를 우선한다.
- 시크릿은 `application.yml` 에 평문으로 두지 말고 환경변수로 주입한다(`${JWT_SECRET}`).

## 로깅
- `System.out.println` / `System.err.println` / `printStackTrace()` **금지**.
- 클래스에 `@Slf4j` 를 붙이고 SLF4J 로거를 쓴다.
- 문자열 연결 대신 파라미터 바인딩을 쓴다.

```java
// 이렇게 쓰지 않는다
System.out.println("예약 생성: " + reservationId);
log.info("예약 생성: " + reservationId);

// 이렇게 쓴다
log.info("예약 생성 완료. reservationId={}", reservationId);
```

- 레벨 기준: `error` 처리 실패, `warn` 복구 가능한 이상, `info` 주요 상태 변화, `debug` 개발용 상세.
- 개인정보 · 토큰 · 결제 정보는 로그에 남기지 않는다.

## 트랜잭션
- `@Transactional` 은 **Service 레이어에서만** 사용한다. Controller 와 Repository 에는 붙이지 않는다.
- 조회 전용 메서드에는 `@Transactional(readOnly = true)` 를 붙인다.
- 외부 API 호출(결제 · 소셜 로그인 · 알림 · 오브젝트 스토리지)은 가능한 한 트랜잭션 밖에서 수행한다. 커넥션을 오래 잡고, 롤백해도 외부 상태는 되돌아가지 않기 때문이다.

## 엔티티
- 엔티티에 `@Setter` **금지**. 무분별한 상태 변경을 막고 변경 지점을 추적 가능하게 유지한다.
- 생성은 정적 팩토리 메서드나 빌더로 한다. 기본 생성자는 JPA 요구사항에 맞춰 `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 로 닫는다.
- 상태 변경은 의도가 드러나는 도메인 메서드로 표현한다.

```java
// 이렇게 쓰지 않는다
reservation.setStatus(ReservationStatus.CANCELED);

// 이렇게 쓴다
reservation.cancel();
```

## Optional
- `Optional` 반환은 **Repository 에서만** 한다.
- Service 는 `Optional` 을 밖으로 흘려보내지 않고 그 자리에서 풀어 예외로 전환한다. Service 메서드 시그니처와 응답 DTO 에 `Optional` 을 노출하지 않는다.

```java
// Repository
Optional<Camp> findByIdAndDeletedFalse(Long campId);

// Service
Camp camp = campRepository.findByIdAndDeletedFalse(campId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CAMP_NOT_FOUND));
```

- `Optional.get()` 은 쓰지 않는다. `orElseThrow` 로 실패 사유를 명시한다.

# 코드 포맷

- **Spotless (Google Java Format)** 를 사용한다. 포맷은 논쟁 대상이 아니라 도구가 정한다.
- 커밋 전 `./gradlew spotlessApply` 로 포맷을 맞추고, `./gradlew spotlessCheck` 로 검증한다. `./gradlew build` 시 자동 검증된다.
- 포맷만 바꾸는 변경은 로직 변경과 같은 커밋에 섞지 않는다. 리뷰에서 실제 변경이 묻힌다.
