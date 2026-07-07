# code-reviewer 메모리

> 이 에이전트가 스스로 쓰고 갱신하는 공간. (이름 고정: MEMORY.md)
> 리뷰하며 알게 된, 코드만으로는 드러나지 않는 사실을 여기에 축적한다.

## 프로젝트 관례
- 계층 흐름: Controller → Service → Repository (단방향).
- 요청/응답은 DTO로만. 엔티티 직접 노출 금지.
- 예외는 BusinessException + ErrorCode, 변환은 GlobalExceptionHandler 담당.
- 스키마 변경은 새 Flyway 마이그레이션(V{n}__*.sql)만. 기존 파일 수정 금지.

## 반복 지적 사항
<!-- 리뷰하며 자주 발견되는 패턴을 여기에 추가 -->
