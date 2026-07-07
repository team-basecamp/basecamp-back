#!/usr/bin/env bash
# 코드 수정 후 자동 포맷 정리 (PostToolUse: Edit|Write 훅에 연결).
# stdin 으로 훅 이벤트 JSON 이 들어온다. 변경된 .java 파일만 처리한다.
set -euo pipefail

payload="$(cat)"

# 편집된 파일 경로 추출 (jq 없으면 조용히 통과)
if ! command -v jq >/dev/null 2>&1; then
  exit 0
fi

file="$(printf '%s' "$payload" | jq -r '.tool_input.file_path // empty')"
[ -z "$file" ] && exit 0

case "$file" in
  *.java)
    # 프로젝트에 포매터 태스크가 있으면 사용 (예: spotless). 없으면 통과.
    if grep -q "spotless" build.gradle 2>/dev/null; then
      ./gradlew spotlessApply -q >/dev/null 2>&1 || true
    fi
    ;;
esac

exit 0
