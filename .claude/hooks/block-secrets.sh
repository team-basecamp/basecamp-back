#!/usr/bin/env bash
# 위험한 명령/시크릿 노출을 차단 (PreToolUse: Bash 훅에 연결).
# exit code 2 -> 도구 실행 차단. stderr 메시지가 Claude 에게 전달된다.
set -euo pipefail

payload="$(cat)"

if ! command -v jq >/dev/null 2>&1; then
  exit 0
fi

cmd="$(printf '%s' "$payload" | jq -r '.tool_input.command // empty')"
[ -z "$cmd" ] && exit 0

# 1) 파괴적 삭제 차단
if printf '%s' "$cmd" | grep -Eq 'rm[[:space:]]+-[a-zA-Z]*rf|rm[[:space:]]+-[a-zA-Z]*fr'; then
  echo "차단: 'rm -rf' 류 파괴적 명령은 허용되지 않습니다." >&2
  exit 2
fi

# 2) 시크릿 파일 커밋/노출 차단
if printf '%s' "$cmd" | grep -Eq 'application-secret\.ya?ml|\.env([^a-zA-Z]|$)'; then
  echo "차단: 시크릿 파일(application-secret.yml / .env)을 다루는 명령이 감지되었습니다." >&2
  exit 2
fi

exit 0
