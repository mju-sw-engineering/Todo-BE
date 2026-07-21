# Todo-BE

## AI 도구 셋업

이 프로젝트는 Codex와 Claude Code에서 사용할 수 있는 AI 워크플로우를 포함합니다.

### 처음 클론 후 설정

```bash
# Git hook 활성화 (main 커밋·시크릿 차단, 커밋 메시지 생성)
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

`git push --force`는 항상 금지됩니다. CodeRabbit 등 리뷰 봇이나 리뷰어 코멘트가 있으면 요약 후 사용자에게 수정할지 그대로 병합할지 확인하며, 기본 동작은 병합 보류입니다. 보안 관련 파일 변경 PR은 사람 리뷰 완료 전 merge하지 않습니다.

### Git Hook

`pre-commit` hook은 main 직접 커밋과 staged 시크릿을 차단합니다.
gitleaks가 없으면 제한된 패턴 폴백을 사용하므로 정밀 검사를 위해 gitleaks 설치를 권장합니다.

`prepare-commit-msg` hook이 활성화된 경우, `git commit` 시 Claude가 staged 변경사항을 분석해 Conventional Commits 형식의 커밋 메시지를 자동 생성합니다.
Claude CLI(`claude`)가 PATH에 없으면 hook은 조용히 스킵됩니다.

### 설정 원본

- 에이전트 규칙: `AGENTS.md`
- 커맨드 원본: `.agents/commands/`
- `.codex/commands/`, `.claude/commands/`는 같은 원본을 가리키는 심링크
- CI는 테스트, 전체/변경분 커버리지, 시크릿, 에이전트 설정, Docker 빌드를 검증

## 컨테이너 배포

`main` push가 발생하면 동일한 이미지를 두 레지스트리에 게시합니다.

- Docker Hub: `yunjin1213/todo:latest`, `yunjin1213/todo:<commit-sha>`
- GHCR: `ghcr.io/mju-sw-engineering/todo-be:latest`, `ghcr.io/mju-sw-engineering/todo-be:<commit-sha>`

Coolify는 전환 기간 동안 기존 Docker Hub 이미지를 계속 사용하며,
이미지 게시가 완료되면 기존 webhook으로 재배포합니다.

### GitHub Actions 설정

필수 Repository Secrets:

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`
- `COOLIFY_BE_WEBHOOK_URL`
- `COOLIFY_API_TOKEN`

GHCR 게시는 GitHub Actions가 자동으로 제공하는 `GITHUB_TOKEN`과
workflow의 `packages: write` 권한을 사용하므로 별도 GHCR Secret은 필요하지 않습니다.
최초 게시 후 GitHub Packages에서 컨테이너 패키지의 공개 범위를 확인해야 합니다.

### 참고 문서

- `AGENTS.md` — AI 에이전트 행동 규칙 및 코딩 컨벤션
