---
description: GitHub 이슈 번호를 받아 원인을 찾아 수정한다
argument-hint: <이슈번호>
---

# /fix-issue

GitHub 이슈 `#$ARGUMENTS`를 찾아 수정한다.

## 절차

1. `gh issue view $ARGUMENTS`로 이슈 내용·재현 조건을 파악한다.
2. 관련 도메인 패키지(`domain/<name>/`)를 특정하고 원인을 진단한다.
3. Controller → Service → Repository 흐름을 지키며 최소 변경으로 수정한다.
4. 회귀 방지 테스트를 추가/수정한다 (`.claude/rules/testing.md` 준수).
5. `./gradlew test`로 검증한다.
6. 변경 요약과 함께 이슈 번호(`Fixes #$ARGUMENTS`)를 커밋 메시지에 남길 준비를 한다.

스키마 변경이 필요하면 기존 마이그레이션을 고치지 말고 새 `V{n}__*.sql`을 추가한다.
