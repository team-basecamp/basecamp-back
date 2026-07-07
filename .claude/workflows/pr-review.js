export const meta = {
  name: 'pr-review',
  description: '변경된 백엔드 코드를 여러 관점으로 병렬 리뷰하고 결과를 종합한다',
  phases: [
    { title: 'Review', detail: '관점별 병렬 리뷰' },
    { title: 'Verify', detail: '발견 사항 검증' },
  ],
}

// 리뷰 관점(차원)
const DIMENSIONS = [
  { key: 'correctness', prompt: '변경된 코드의 논리 오류·널 처리·트랜잭션/동시성 문제를 찾아라.' },
  { key: 'layering',    prompt: 'Controller→Service→Repository 단방향 규칙 위반, 엔티티 직접 노출을 찾아라.' },
  { key: 'security',    prompt: '.claude/skills/security-review/checklist.md 기준으로 인증/인가·입력검증·시크릿 노출을 점검하라.' },
  { key: 'tests',       prompt: '변경에 대한 테스트 누락·부적절을 .claude/rules/testing.md 기준으로 찾아라.' },
]

const FINDINGS_SCHEMA = {
  type: 'object',
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          severity: { type: 'string', enum: ['high', 'medium', 'low'] },
          file: { type: 'string' },
          line: { type: 'number' },
          summary: { type: 'string' },
          suggestion: { type: 'string' },
        },
        required: ['severity', 'file', 'summary'],
      },
    },
  },
  required: ['findings'],
}

const VERDICT_SCHEMA = {
  type: 'object',
  properties: {
    isReal: { type: 'boolean' },
    reason: { type: 'string' },
  },
  required: ['isReal', 'reason'],
}

// 각 관점을 리뷰하고, 나온 발견 사항을 즉시 검증한다 (pipeline: 배리어 없음).
const results = await pipeline(
  DIMENSIONS,
  d => agent(`변경분(git diff)을 리뷰하라. ${d.prompt}`, {
    label: `review:${d.key}`, phase: 'Review', schema: FINDINGS_SCHEMA,
  }),
  review => parallel((review?.findings ?? []).map(f => () =>
    agent(`다음 지적이 실제 문제인지 반박을 시도해 검증하라: ${f.summary} (${f.file}:${f.line ?? '?'})`, {
      label: `verify:${f.file}`, phase: 'Verify', schema: VERDICT_SCHEMA,
    }).then(v => ({ ...f, verdict: v }))
  )),
)

const confirmed = results.flat().filter(Boolean).filter(f => f.verdict?.isReal)
return { confirmed }
