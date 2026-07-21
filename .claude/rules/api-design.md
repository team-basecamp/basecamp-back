---
description: 백엔드 API(컨트롤러/DTO/예외)를 다룰 때 적용되는 규칙
globs:
  - "src/main/java/com/basecamp/backend/domain/**/controller/**"
  - "src/main/java/com/basecamp/backend/domain/**/dto/**"
  - "src/main/java/com/basecamp/backend/common/exception/**"
---

# API 설계 규칙

## URL / 메서드
- 리소스 기반 복수형 명사: `/api/v1/reservations`, `/api/v1/camps/{campId}/reviews`.
- **GET / POST 두 가지만 사용한다.** 보안 정책상 PUT / PATCH / DELETE 는 허용되지 않으며, 사용하면 클라이언트 요청이 **405 로 거부**될 수 있다.
  - 조회 → `GET`
  - 생성 · 수정 · 삭제 · 상태 전이 → 전부 `POST`
- 조회가 아닌 동작은 URL 마지막 세그먼트에 동사를 둬서 구분한다: `POST /{resource}/{id}/{action}`.

| 동작 | 쓰지 않음 | 사용 |
|------|-----------|------|
| 수정 | `PATCH /camps/{campId}` | `POST /camps/{campId}/update` |
| 삭제 | `DELETE /camps/{campId}` | `POST /camps/{campId}/delete` |
| 상태 전이 | `PATCH /reservations/{id}` | `POST /reservations/{id}/cancel` |

- 기존 구현 참고: `ReservationController` 의 `/{id}/cancel` · `/approve` · `/reject`, `AdminCampOwnerController` 의 `/{id}/approve` · `/reject`.
- 삭제를 POST 로 표현하므로, **의도치 않은 재요청에 대비해 서버에서 멱등하게 처리**한다(이미 삭제된 리소스 재삭제 시 에러 대신 성공 또는 명시적 `ErrorCode`).

## 요청 / 응답
- 엔티티를 직접 노출하지 않는다. 항상 `dto/request`, `dto/response`의 DTO 사용.
- 요청 DTO에는 Bean Validation(`@NotNull`, `@Size`, `@Email` 등)을 붙이고 컨트롤러에서 `@Valid`로 검증.
- 응답 본문은 일관된 형태 유지. 생성은 `201 Created`, 조회 성공은 `200 OK`.

## 공통 응답 봉투 (ApiResponse)
- 모든 응답은 `common/response/ApiResponse` 봉투로 통일된다: 성공 `{ success:true, message, data }`, 실패 `{ success:false, code, message, errors }`.
- 컨트롤러는 **순수 DTO만 반환**한다. `ApiResponseWrapAdvice`(ResponseBodyAdvice)가 `com.basecamp.backend.domain` 컨트롤러의 정상 응답을 자동으로 봉투에 감싸므로 직접 감싸지 말 것.
- 오류 봉투는 `GlobalExceptionHandler`가 생성한다. 필터 단계(401/403) 오류는 `SecurityResponseWriter`가 같은 봉투로 직렬화한다.
- 프론트(`instance.ts` 인터셉터)는 성공 시 `data`만 언래핑해 받으므로, 프론트 코드는 봉투를 몰라도 순수 DTO를 그대로 쓴다.

## 예외 처리
- 비즈니스 오류는 `throw new BusinessException(ErrorCode.XXX)`로 던진다.
- 새 오류 상황은 `ErrorCode` enum에 추가하고, 변환은 `GlobalExceptionHandler`에만 둔다.
- 컨트롤러/서비스에서 직접 `ResponseEntity`로 에러를 조립하지 않는다.

## 문서화
- 새 엔드포인트에는 springdoc 애노테이션(`@Operation`, `@Schema`)으로 설명을 남긴다.
