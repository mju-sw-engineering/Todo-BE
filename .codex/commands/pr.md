# /pr - PR 설명 초안 작성

## 역할
현재 브랜치와 main의 차이를 분석하여 PR 설명 초안을 작성한다.
push와 `gh pr create`는 사용자가 직접 실행한다.

## 실행 순서

### 1. 브랜치 및 변경사항 파악
```bash
git branch --show-current
git log main..HEAD --oneline
git diff main...HEAD --stat
git diff main...HEAD
```

### 2. PR 템플릿 확인
`.github/pull_request_template.md`가 있으면 해당 형식을 그대로 따른다.

현재 프로젝트의 PR 템플릿:
```
# 요약
# 작업 내용
# 기타 (논의하고 싶은 부분)
# 타 직군 전달 사항
```

### 3. PR 설명 작성

PR 템플릿을 기반으로 작성하되, 다음 내용을 포함한다.

**요약**
- 이 PR이 무엇을 하는지 한 문장으로

**작업 내용**
- 주요 변경사항을 체크리스트로 (커밋 단위가 아니라 기능/의도 단위로)

**기술적 변경 포인트** (리뷰어를 위한 안내)
- 설계상 중요한 결정 사항
- 리뷰어가 특히 봐야 할 부분

**테스트 방법**
- 로컬에서 확인하는 방법
- 테스트 케이스 실행 명령어

**Breaking Change 여부**
- API 변경, DB 스키마 변경, 환경변수 추가 등 배포 시 추가 작업 필요 여부

**보안 관련 변경 포함 여부**
- SecurityConfig, JwtUtil, JwtAuthenticationFilter 등 수정 포함 시 명시

### 4. 초안 저장 및 보고
`.ai-workspace/pr.md`에 저장하고 사용자에게 전문을 보여준다.

### 5. push 및 PR 생성 안내
사용자가 직접 실행할 명령어를 안내한다.
```bash
# 브랜치 push
git push origin <브랜치명>

# PR 생성 (gh CLI 사용 시)
gh pr create --title "<제목>" --body "$(cat .ai-workspace/pr.md)"
```

## 주의사항
- push 자동 실행 절대 금지
- `gh pr create` 자동 실행 절대 금지
- 1000줄 이상 diff인 경우 전체 분석 대신 `--stat` 기반으로 요약하고 사용자에게 알림
- main 브랜치에서 실행 중이라면 경고 후 중단
