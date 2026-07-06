---
description: 테스트 코드를 작성하거나 수정할 때 적용되는 규칙
globs:
  - "src/test/**/*.java"
---

# 테스트 규칙

- 프레임워크: JUnit 5 + Spring Boot Test + Spring Security Test.
- 테스트 클래스명은 `{대상클래스}Test`, 메서드명은 `동작_조건_기대결과` 형태.
- 계층별 전략:
  - **Service**: 순수 단위 테스트 우선. 의존 리포지토리는 Mockito로 목킹.
  - **Controller**: `@WebMvcTest` + `MockMvc`로 요청/응답·검증만 확인.
  - **Repository**: `@DataJpaTest`로 쿼리 동작 확인.
- 외부 연동(결제/소셜 로그인/알림)은 실제 호출 대신 목/스텁 사용.
- 실행: `./gradlew test`. 하나만: `./gradlew test --tests "*ReservationServiceTest"`.
- given–when–then 주석으로 구간을 구분한다.
