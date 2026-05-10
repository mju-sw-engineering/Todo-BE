# Todo-BE

## AI 도구 셋업

이 프로젝트는 Claude Code와 연동된 AI 워크플로우를 포함합니다.

### 처음 클론 후 설정

```bash
# Git hook 활성화 (커밋 메시지 자동 생성)
bash scripts/setup-hooks.sh
```

### 슬래시 커맨드 (Claude Code에서 사용)

| 커맨드 | 설명 |
|---|---|
| `/plan <기능 설명>` | 구현 계획 수립 (코드 수정 없음) |
| `/impl` | plan.md 기반 구현 |
| `/review` | 변경사항 셀프 리뷰 |
| `/commit` | 커밋 메시지 제안 및 커밋 |
| `/pr` | PR 설명 초안 작성 |
| `/feature <기능 설명>` | plan → impl → test → review → commit → pr 전체 워크플로우 |

### Git Hook

`prepare-commit-msg` hook이 활성화된 경우, `git commit` 시 Claude가 staged 변경사항을 분석해 Conventional Commits 형식의 커밋 메시지를 자동 생성합니다.
Claude CLI(`claude`)가 PATH에 없으면 hook은 조용히 스킵됩니다.

### 참고 문서

- `AGENTS.md` — AI 에이전트 행동 규칙 및 코딩 컨벤션