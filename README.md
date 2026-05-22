# Todo-BE

## AI 도구 셋업

이 프로젝트는 Codex와 Claude Code에서 사용할 수 있는 AI 워크플로우를 포함합니다.

### 처음 클론 후 설정

```bash
# Git hook 활성화 (커밋 메시지 자동 생성)
bash scripts/setup-hooks.sh
```

### 슬래시 커맨드 (Codex / Claude Code에서 사용)

| 커맨드 | 설명 |
|---|---|
| `/plan <기능 설명>` | 구현 계획 수립 (코드 수정 없음) |
| `/impl` | plan.md 기반 구현 |
| `/review` | 변경사항 셀프 리뷰 |
| `/commit` | 커밋 메시지 제안 및 승인 후 커밋, 별도 승인 후 push |
| `/pr` | PR 설명 작성 및 승인 후 브랜치 push/PR 생성 |
| `/merge` | PR 체크/리뷰 확인 및 승인 후 병합 |
| `/feature <기능 설명>` | plan → impl → test → review → commit → push 승인 → pr 전체 워크플로우 |

### Git 자동화 승인 흐름

AI는 다음 단계별로 사용자 승인을 받은 경우에만 Git 상태 변경 명령을 실행합니다.

1. 커밋 메시지 승인 후 `git add <파일 목록>` 및 `git commit`
2. 커밋 이후 별도 승인 후 `git push origin <현재 브랜치명>`
3. PR 제목/본문 확인 및 승인 후 `gh pr create`
4. 사용자가 merge를 요청하면 체크와 사람/봇 리뷰를 확인한 뒤, 수정 또는 병합 지시를 받아 `gh pr merge`

`git push --force`는 항상 금지됩니다. CodeRabbit 등 리뷰 봇이나 리뷰어 코멘트가 있으면 요약 후 사용자에게 수정할지 그대로 병합할지 확인합니다. 보안 관련 파일 변경 PR은 사람 리뷰 완료 전 merge하지 않습니다.

### Git Hook

`prepare-commit-msg` hook이 활성화된 경우, `git commit` 시 Claude가 staged 변경사항을 분석해 Conventional Commits 형식의 커밋 메시지를 자동 생성합니다.
Claude CLI(`claude`)가 PATH에 없으면 hook은 조용히 스킵됩니다.

### 참고 문서

- `AGENTS.md` — AI 에이전트 행동 규칙 및 코딩 컨벤션
