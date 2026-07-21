# AGENTS.md

이 파일은 AI 에이전트(Claude 등)가 이 레포지토리에서 작업할 때 따라야 할 규칙과 컨텍스트를 정의합니다.

---

## 프로젝트 개요

**Todo 백엔드 API 서버** — Spring Boot 기반의 REST API.

| 항목 | 내용 |
|---|---|
| 프레임워크 | Spring Boot 3.4.5 |
| Java | 17 |
| 빌드 도구 | Gradle 8.13 (Groovy DSL) |
| 데이터베이스 | MySQL |
| 인증 | JWT (jjwt 0.12.3) + Spring Security |
| 파일 스토리지 | S3 호환 오브젝트 스토리지 (AWS SDK v2) |
| 실시간 통신 | Spring WebSocket/STOMP |
| 모듈 구조 | 단일 모듈 |

**주요 의존성**
- `spring-boot-starter-web` — REST API
- `spring-boot-starter-data-jpa` — JPA/Hibernate
- `spring-boot-starter-security` — 인증/인가
- `spring-boot-starter-validation` — 입력 검증
- `spring-boot-starter-websocket` — 채팅 및 실시간 상태 전달
- `spring-boot-starter-mail` — 이메일 인증 및 팀 초대 메일
- `springdoc-openapi-starter-webmvc-ui:2.7.0` — Swagger UI
- `software.amazon.awssdk:s3` — S3 파일 업로드/조회
- `thumbnailator` — 인증 사진 썸네일 생성
- `lombok` — 보일러플레이트 코드 제거
- `mysql-connector-j` — MySQL 드라이버

---

## 빌드 / 테스트 / 실행 명령어

```bash
# 전체 빌드
./gradlew build

# 컴파일만 (빠른 확인)
./gradlew compileJava

# 테스트 실행
./gradlew test

# 로컬 실행 (application-local.yml 필요)
./gradlew bootRun

# 빌드 결과물 정리
./gradlew clean

# 빌드 + 테스트 스킵 (빠른 jar 생성)
./gradlew build -x test
```

**최초 셋업 (clone 후 1회)**
```bash
bash scripts/setup-hooks.sh
```
- main 직접 커밋과 staged 시크릿을 차단하는 Git hook을 활성화한다.
- 로컬에 gitleaks가 없으면 제한된 패턴 폴백을 사용하며, CI의 secret-scan이 최종 방어선이다.

**로컬 실행 전 필요한 설정** (`src/main/resources/application-local.yml`):
- DB 접속 정보 (`spring.datasource.url`, `username`, `password`)
- JWT 시크릿 (`jwt.secret`)
- 메일 서버 정보 (`spring.mail.*`)
- S3 호환 스토리지 정보 (`minio.endpoint`, `access-key`, `secret-key`, `bucket`)
- 프론트엔드 기준 URL (`app.frontend-base-url`)

---

## 패키지 / 모듈 구조

```
com.todo
├── TodoApplication.java
├── domain/                         # 비즈니스 도메인
│   ├── auth/                       # 로그인, 회원가입, 이메일 인증
│   ├── user/                       # 마이페이지, 닉네임 수정, 회원 탈퇴
│   ├── team/                       # 팀 생성/초대/가입, AI 페르소나
│   ├── todo/                       # 할 일 생성/제출/반응/리포트
│   ├── chat/                       # 팀 채팅, 읽음 상태, 타이핑 상태
│   ├── notification/               # 사용자 알림과 미읽음 개수
│   └── evaluation/                 # 일일 평가 생성 및 AI 서버 연동
└── global/                         # 공통/인프라
    ├── config/                     # Security, CORS, Swagger, S3, WebSocket 설정
    ├── exception/                  # 전역 예외 처리
    ├── jwt/                        # JWT 유틸리티, 필터
    ├── websocket/                  # STOMP CONNECT 인증 인터셉터
    ├── response/                   # 공통 응답 형식 (ApiResponse)
    └── service/                    # 파일 업로드/썸네일 등 공통 서비스
```

**도메인 책임 경계**

| 도메인 | 책임 |
|---|---|
| `auth` | 로그인, 회원가입, 토큰 발급/검증 |
| `user` | 사용자 조회/수정/탈퇴 |
| `team` | 팀과 팀원, 초대, AI 페르소나 관리 |
| `todo` | 팀 할 일, 참가자 제출, 반응, 기간별 리포트 |
| `chat` | WebSocket 채팅과 읽음/타이핑 상태 |
| `notification` | 도메인 이벤트에 따른 사용자 알림 |
| `evaluation` | Todo 통계 기반 AI 일일 평가 |
| `global` | 설정, 공통 응답, 예외, JWT, WebSocket, 파일 인프라 |

---

## 코딩 컨벤션

### 현재 코드베이스 상태 vs. 신규 코드 기준

아래 표는 현재 코드에 불일치가 있는 항목과 앞으로 지켜야 할 표준을 명시합니다.
신규 코드는 반드시 신규 기준을 따릅니다.
기존 코드는 기능 수정 시 함께 마이그레이션합니다.

---

#### 1. Request DTO

| | 현재 코드 | 신규 기준 |
|---|---|---|
| `LoginRequest` | `record` (기준 충족) | `record` |
| `SignupRequest` | `class + @Getter/@Setter` (마이그레이션 대상) | 점진적 마이그레이션 |

**규칙**
- 신규 Request DTO는 무조건 `record`로 작성
- `@ModelAttribute` 바인딩이 있는 기존 class DTO는 record 전환 전 반드시 동작 검증 후 마이그레이션
- DTO는 불변으로 유지 (setter 추가 금지)

```java
// 올바른 예
public record CreateTaskRequest(
    @NotBlank
    @Schema(description = "할 일 제목")
    String title,

    @Schema(description = "마감일")
    LocalDate dueDate
) {}

// 금지
@Getter @Setter
public class CreateTaskRequest {
    private String title;
}
```

---

#### 2. Response DTO

| | 현재 코드 | 신규 기준 |
|---|---|---|
| `SignupResponse` | `record` + `from(User)` (기준 충족) | `record` + `from(Entity)` |
| `LoginResponse` | `record`, `from()` 없음 (마이그레이션 대상) | 마이그레이션 대상 |

**규칙**
- 모든 Response DTO는 `record`
- Entity를 받아 DTO를 생성하는 `from(Entity entity)` static factory method 필수
- 서비스 레이어에서 `new ResponseDto(field1, field2)` 직접 생성 금지

```java
// 올바른 예
public record TaskResponse(
    @Schema(description = "할 일 ID") Long id,
    @Schema(description = "제목") String title
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle());
    }
}

// 금지 (서비스에서 직접 생성)
return new TaskResponse(task.getId(), task.getTitle());
```

---

#### 3. Validation 어노테이션 배치

**규칙**: 어노테이션이 하나여도 반드시 별도 줄에 작성. 한 줄에 몰아쓰기 금지.

```java
// 올바른 예
public record LoginRequest(
    @NotBlank
    @Schema(description = "로그인 아이디", example = "user123")
    String loginId,

    @NotBlank
    @Schema(description = "비밀번호", example = "password123!")
    String password
) {}

// 금지
public record LoginRequest(
    @NotBlank @Schema(description = "로그인 아이디", example = "user123") String loginId
) {}
```

---

#### 4. 예외 처리

| | 현재 코드 | 신규 기준 |
|---|---|---|
| `AuthService` | `IllegalArgumentException` 직접 throw (마이그레이션 대상) | `BusinessException` 사용 |
| `FileService` | `RuntimeException` 직접 throw (마이그레이션 대상) | 커스텀 예외 사용 |
| `GlobalExceptionHandler` | `IllegalArgumentException`, `MethodArgumentNotValidException`만 처리 (마이그레이션 대상) | 모든 예외를 `ApiResponse` 형식으로 처리 |

**규칙**
- `IllegalArgumentException`, `RuntimeException` 직접 throw 금지
- 최소 예외 계층:
  ```
  BusinessException(message, HttpStatus)  <- 비즈니스 규칙 위반
  FileStorageException                    <- 파일 저장/읽기 실패
  ```
- `GlobalExceptionHandler`는 `BusinessException` + 미처리 예외(`Exception`) 모두 `ApiResponse` 형식으로 응답

```java
// 올바른 예
throw new BusinessException("이미 사용 중인 아이디입니다.", HttpStatus.BAD_REQUEST);

// 금지
throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
throw new RuntimeException("파일 저장에 실패했습니다.", e);
```

---

#### 5. Swagger 어노테이션 import 충돌 처리

Swagger의 `ApiResponse`와 프로젝트의 `ApiResponse` 이름 충돌 해결 방법:

```java
// 규칙: 우리 ApiResponse는 import, Swagger ApiResponse는 FQN
import com.todo.global.response.ApiResponse;

@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", ...)
ResponseEntity<ApiResponse<TaskResponse>> getTask(...);

// 금지: 반대로 적용
import io.swagger.v3.oas.annotations.responses.ApiResponse;
ResponseEntity<com.todo.global.response.ApiResponse<TaskResponse>> getTask(...);
```

---

#### 6. Entity 작성 규칙

현재 `User` 엔티티 패턴을 표준으로 삼는다.

- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 필수 (JPA 요구 + 외부 직접 생성 방지)
- setter 금지, 상태 변경은 의미 있는 메서드로 표현
- 생성은 `static factory method` (`create(...)`) 패턴 사용
- `@CreatedDate`, `@LastModifiedDate` JPA Auditing 사용

```java
// 현재 User 엔티티 패턴 — 표준
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {
    public static Task create(String title, User user) { ... }
    public void updateTitle(String title) { ... }  // setter 대신 의미 있는 메서드
}
```

---

#### 7. Controller 작성 규칙

- `@RestController` + `@RequiredArgsConstructor`
- Swagger 문서는 별도 `*ControllerDocs` 인터페이스로 분리
- 반환 타입: `ResponseEntity<ApiResponse<T>>`
- `@RequestMapping`으로 기본 경로 지정

---

#### 8. Service 작성 규칙

- `@Service` + `@RequiredArgsConstructor`
- 클래스 레벨: `@Transactional(readOnly = true)`
- 쓰기 작업 메서드: `@Transactional` 개별 적용
- 의존성 주입: 생성자 주입만 (`@Autowired` 필드 주입 금지)

---

#### 9. 도메인 책임 경계

- `AuthService`: 인증/인가만 담당 (로그인, 회원가입, 토큰 발급)
  - `UserDetailsService` 구현은 현재 허용
  - 사용자 조회/수정/탈퇴 기능은 `UserService`가 담당
- 사용자 CRUD를 `AuthService`에 추가하지 않는다
- 외부 AI 호출은 `evaluation/client` 경계를 통해 수행한다
- 실시간 인증 변경 시 REST Security와 WebSocket CONNECT 인증을 함께 검토한다

---

## 테스트 컨벤션

**작성 의무**: 새 서비스 로직 또는 기존 비즈니스 로직 변경 시 관련 테스트를 함께 작성한다.
구현 완료의 정의에는 컴파일과 전체 테스트 통과가 포함된다.

| 계층 | 기본 전략 |
|---|---|
| Entity | 상태 전이와 불변식 단위 테스트 |
| Service | JUnit 5 + Mockito 단위 테스트 |
| Repository | 쿼리 동작이 중요할 때 `@DataJpaTest` |
| Controller | 요청 검증·인증·응답 계약 중심 테스트 |
| Security/WebSocket | 필터·인터셉터의 허용/거부 경계 테스트 |

**작성 규칙**
- 현재 코드 스타일에 맞춰 동작과 기대 결과가 드러나는 한글 테스트 이름을 사용한다
- 정상 케이스와 주요 예외/경계 케이스를 각각 검증한다
- assertion 없는 테스트나 커버리지 수치만 올리는 getter/setter 테스트는 금지한다
- 외부 AI, 메일, S3 의존성은 단위 테스트에서 mock으로 격리한다
- PR 변경 코드의 patch line coverage 80% 이상을 유지한다
- 전체 line coverage 85%, branch coverage 74%를 하한선으로 유지한다

---

## Git 워크플로우

- 브랜치 전략: `main` <- PR로만 병합
- 브랜치 네이밍: `feat/기능명`, `fix/버그명`, `refactor/대상`, `chore/작업명`
- 커밋 형식: Conventional Commits (한글 제목, 50자 이내)
  ```
  feat: 할 일 목록 조회 API 추가
  fix: 로그인 시 비밀번호 검증 오류 수정
  ```
- PR은 `.github/pull_request_template.md` 형식 준수
- 커밋 작성자 이름과 이메일은 각 개발자의 Git 설정을 사용하며 저장소에서 공통 identity를 강제하지 않는다
- 커밋, PR, merge commit에는 `Co-authored-by`, `Generated-by`, 에이전트 이름·이메일, AI 도구 라벨 등 작업 도구를 식별하는 metadata를 자동으로 남기지 않는다
- 공동 작성자 또는 별도 라벨은 사용자가 명시적으로 요청한 경우에만 추가한다

---

## Git 자동화 승인 게이트

AI는 Git 작업을 자동화할 수 있지만, 아래 게이트마다 사용자 승인을 반드시 받아야 한다.

1. **커밋 메시지 승인**
   - 변경사항 분석 후 커밋 메시지를 제안한다.
   - 사용자가 승인한 경우에만 `git add <파일 목록>` 및 `git commit`을 실행한다.
2. **브랜치 push 승인**
   - 커밋 완료 후 push 대상 브랜치와 명령어를 보여준다.
   - 사용자가 승인한 경우에만 `git push origin <현재 브랜치명>`을 실행한다.
3. **PR 생성 승인**
   - PR 제목과 본문을 `.ai-workspace/pr.md`에 작성하고 전문을 보여준다.
   - 사용자가 승인한 경우에만 `gh pr create`를 실행한다.
4. **PR merge 승인**
   - 사용자가 명시적으로 merge를 요청한 경우에만 PR 상태, 체크 결과, 사람/봇 리뷰를 확인한다.
   - CodeRabbit 등 리뷰 봇 또는 리뷰어가 수정 요청/코멘트를 남겼다면 요약하고 사용자에게 "수정할지" 또는 "그냥 머지할지"를 묻는다.
   - merge 방식(`merge`, `squash`, `rebase`)과 실행 명령어를 제안하고 사용자 승인을 받은 뒤 `gh pr merge`를 실행한다.

**자동화 원칙**
- 승인 전에는 다음 단계의 Git 상태 변경 명령을 실행하지 않는다.
- 승인 요청에는 실행할 명령어, 대상 브랜치/PR, 검증 결과를 함께 표시한다.
- 사용자가 "취소", "중단" 또는 동등한 의사를 밝히면 즉시 중단한다.
- 리뷰 코멘트 확인 중 수정 필요 사항이 발견되면 기본 동작은 병합 보류이며, 사용자의 "수정해줘" 또는 "그냥 머지해줘" 같은 명시 지시를 기다린다.
- 보안 관련 파일(`SecurityConfig`, `JwtUtil`, `JwtAuthenticationFilter`, `WebSocketAuthChannelInterceptor` 등)이 변경된 PR은 사람 리뷰 완료 전 merge 금지.

---

## 절대 규칙

다음 행동은 어떤 상황에서도 금지된다.

1. **승인 없는 push 금지** — AI는 사용자 승인 후에만 `git push` 실행 가능
2. **force push 금지** — `git push --force` 절대 실행 금지
3. **main 직접 커밋 금지** — main 브랜치에 직접 commit 금지
4. **민감정보 커밋 금지** — API 키, 비밀번호, JWT 시크릿, DB 접속 정보 등 파일에 직접 작성 및 커밋 금지
5. **보안 변경 사람 리뷰 필수** — `SecurityConfig`, `JwtUtil`, `JwtAuthenticationFilter`, `WebSocketAuthChannelInterceptor` 등 보안 관련 파일 수정 시 반드시 사람이 리뷰한 후 병합
6. **커밋 전 사용자 승인 필수** — AI가 자동으로 커밋 메시지를 확정하고 커밋 실행 금지. 항상 메시지 제안 후 승인 대기
7. **AI 작업 metadata 자동 추가 금지** — 커밋, PR, merge commit에 공동 작성자 trailer, 에이전트 이름·이메일, AI 라벨을 자동으로 추가하지 않는다

---

## Codex 커맨드 워크플로우

커맨드 원본은 `.agents/commands/`에 있다.
`.codex/commands/`와 `.claude/commands/`는 이 디렉터리를 가리키는 심링크이므로
커맨드 수정은 반드시 `.agents/commands/`에서만 한다.

Codex 또는 Claude Code가 아래 명령을 요청받으면
`.agents/commands/<command>.md` 파일을 먼저 읽고 해당 절차를 따른다.

| 명령 | 파일 | 용도 |
|---|---|---|
| `/feature` | `.agents/commands/feature.md` | 계획부터 PR 생성까지 전체 기능 워크플로우 |
| `/plan` | `.agents/commands/plan.md` | 구현 전 계획 수립 |
| `/impl` | `.agents/commands/impl.md` | 승인된 계획 기반 구현 |
| `/review` | `.agents/commands/review.md` | 변경사항 셀프 리뷰 |
| `/commit` | `.agents/commands/commit.md` | 커밋 메시지 제안 및 승인 후 커밋 |
| `/pr` | `.agents/commands/pr.md` | PR 설명 작성 및 승인 후 push/PR 생성 |
| `/merge` | `.agents/commands/merge.md` | 승인 후 PR 병합 |

**적용 규칙**
- `.agents/commands`의 내용이 AGENTS.md와 충돌하면 AGENTS.md를 우선한다.
- 커맨드 파일을 읽었더라도 절대 규칙은 항상 유지한다.
- `/commit`은 커밋 메시지 제안 후 사용자 승인을 받은 경우에만 실행한다.
- `/pr`은 PR 제목/본문을 작성하고, 사용자 승인 후 브랜치 push 및 `gh pr create`를 실행할 수 있다.
- `/merge`는 사용자가 명시적으로 병합을 요청하고 승인한 경우에만 실행한다.

---

## 계획·핸드오프 상태 문서

- `.ai-workspace/plan.md`, `pr.md` 상단에 작성일·브랜치·기준 HEAD를 기록한다
- 브랜치나 작업 내용이 다른 상태 문서를 다음 구현에 재사용하지 않는다
- 작업 완료 또는 폐기 시 완료 상태와 날짜를 상단에 기록한다
- 영구 규칙은 상태 문서가 아니라 AGENTS.md로 이관한다
