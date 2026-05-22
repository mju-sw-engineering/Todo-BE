# /pr — PR 설명 작성 및 생성

## 역할
현재 브랜치와 main의 차이를 분석하여 PR 설명을 작성한다.
사용자 승인 후 브랜치를 push하고 `gh pr create`로 PR을 생성할 수 있다.

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

### 4. PR 설명 저장 및 보고
`.ai-workspace/pr.md`에 저장하고 사용자에게 전문을 보여준다.

### 5. 브랜치 push 승인 대기
현재 브랜치의 upstream 및 미push 커밋 여부를 확인한다.

```bash
git branch --show-current
git status -sb
git rev-parse --abbrev-ref --symbolic-full-name @{u} >/dev/null 2>&1
```

upstream 확인이 성공하면 미push 커밋을 확인한다.

```bash
git log @{u}..HEAD --oneline
```

upstream이 없거나 미push 커밋이 있으면 실행할 명령어를 보여주고 승인 여부를 묻는다.

```bash
git push origin <현재 브랜치명>
```

**사용자 응답별 처리**
- 승인: `git push origin <현재 브랜치명>` 실행
- 보류/취소: push 및 PR 생성을 중단
- 이미 원격 브랜치가 최신임: push 없이 STEP 6 진행

### 6. PR 생성 승인 대기
push가 성공하면 PR 제목, PR 본문, 실행할 명령어를 보여주고 승인 여부를 묻는다.

```bash
gh pr create --title "<제목>" --body "$(cat .ai-workspace/pr.md)"
```

**사용자 응답별 처리**
- 승인: `gh pr create` 실행
- 수정: PR 제목/본문을 수정한 뒤 다시 승인 요청
- 취소: PR 생성을 중단

### 7. PR 생성 후 보고
PR URL을 사용자에게 보고한다.
사용자가 merge를 원하면 `/merge` 절차로 이어간다.

## 주의사항
- push와 `gh pr create`는 각각 별도 승인 후에만 실행
- `git push --force` 또는 `--force-with-lease` 사용 절대 금지
- 1000줄 이상 diff인 경우 전체 분석 대신 `--stat` 기반으로 요약하고 사용자에게 알림
- main 브랜치에서 실행 중이라면 경고 후 중단
