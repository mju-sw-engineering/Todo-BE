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
| 데이터베이스 | MySQL, 스키마는 Flyway 마이그레이션으로 관리 |
| 인증 | JWT (jjwt 0.12.3) + Spring Security, Apple 로그인(Sign in with Apple) |
| 파일 스토리지 | S3 호환 오브젝트 스토리지 (AWS SDK v2) |
| 실시간 통신 | Spring WebSocket/STOMP |
| AI | OpenAI Responses API (인증 파일 판정·요약). 별도 AI 서버 없음 |
| 모듈 구조 | 단일 모듈 |

**주요 의존성**
- `spring-boot-starter-web` — REST API
- `spring-boot-starter-data-jpa` — JPA/Hibernate
- `flyway-core` / `flyway-mysql` — 스키마 마이그레이션
- `spring-boot-starter-security` — 인증/인가
- `spring-boot-starter-validation` — 입력 검증
- `spring-boot-starter-websocket` — 채팅 및 실시간 상태 전달
- `spring-boot-starter-mail` — 이메일 인증, 비밀번호 재설정, 팀 초대 메일
- `spring-boot-starter-actuator` — health/readiness probe
- `springdoc-openapi-starter-webmvc-ui:2.7.0` — Swagger UI
- `software.amazon.awssdk:s3` — S3 파일 업로드/조회/presigned URL
- `thumbnailator` + `imageio-webp` — 인증 사진 썸네일 생성 (WebP 포함)
- `pdfbox` / `poi-ooxml` / `commons-csv` — 인증 문서(PDF/DOCX/XLSX/CSV) 텍스트 추출 후 AI 요약
- `lombok` — 보일러플레이트 코드 제거
- `mysql-connector-j` — MySQL 드라이버
- 테스트: `spring-boot-starter-test`, H2(기본), Testcontainers MySQL(`mysql` 태그)

---

## 빌드 / 테스트 / 실행 명령어

```bash
# 전체 빌드 (test + 전체 커버리지 게이트 포함)
./gradlew build

# 컴파일만 (빠른 확인)
./gradlew compileJava

# 테스트 실행 (H2 기반, Docker 불필요)
./gradlew test

# MySQL Testcontainers 테스트 (Flyway 실제 적용·MySQL 전용 제약·동시성, Docker 필요)
./gradlew mysqlTest

# 전체 커버리지 하한선 검사 (CI와 동일)
./gradlew test jacocoTestCoverageVerification

# Flyway 마이그레이션 버전 검사 (중복·역순, CI와 동일)
BASE_REF=main bash scripts/check-migrations.sh

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
`.gitignore` 대상이라 커밋되지 않는다. 운영 값을 넣지 않는다.

| 설정 | 비고 |
|---|---|
| `spring.datasource.url` / `username` / `password` | |
| `jwt.secret` | HS256이라 32바이트(영문 32자) 이상 |
| `spring.mail.*` | 이메일 인증·비밀번호 재설정·팀 초대 메일 발송 |
| `minio.endpoint` / `access-key` / `secret-key` / `region` / `bucket` | 프로필·인증 파일 업로드 |
| `swagger.auth.username` / `password` | **`application.yml`이 환경변수를 참조하는데 기본값이 없다.** 환경변수도 로컬 설정도 없으면 기동이 실패한다 |
| `apple.team-id` / `web-client-id` / `ios-client-id` / `key-id` / `private-key` | **`AppleProperties`가 `@NotBlank`로 검증한다.** 값이 없으면 기동이 실패한다. 로컬에서 Apple 로그인을 쓰지 않아도 placeholder 문자열은 넣어야 한다 (`src/test/resources/application-test.yml` 참고) |
| `openai.api-key` | **선택.** 없어도 기동되며 인증 파일 AI 판정만 영구 실패(`FAILED`)로 처리된다. AI 판정을 로컬에서 확인할 때만 넣는다 |
| `spring.jpa.hibernate.ddl-auto` | 아래 참조 |

기본값이 있어 생략 가능한 것: `app.*`(frontend-base-url, api-server-url, team-invite-path 등), `todo.scheduling.*`, `proof-analysis.*`, `openai.model`/`reasoning-effort`/`max-output-tokens`, `chat.cleanup.*`, `mail.async.*`, `file-deletion.*`, `cookie.secure`

**`cookie.secure`는 기본값이 `true`다.** 리프레시 토큰 쿠키에 `Secure`가 붙어 HTTPS에서만 오간다. 백엔드를 `http://localhost`로 직접 띄우고 브라우저에서 로그인·갱신·로그아웃을 확인하려면 `application-local.yml`에 `cookie.secure: false`를 넣는다. Chrome과 Firefox는 `localhost`를 신뢰 가능한 출처로 취급해 `true`인 채로도 대체로 동작하지만 Safari는 쿠키를 거부한다. 운영은 `application-prod.yml`에서 `true`로 고정돼 있어 환경변수로도 내려가지 않는다.

**`ddl-auto`는 운영과 같은 `validate`를 쓴다.** 스키마는 Flyway가 만들고 Hibernate는 대조만 한다. `update`로 두면 엔티티와 마이그레이션이 어긋나도 Hibernate가 조용히 `ALTER`로 고쳐버려, 로컬에서는 멀쩡한데 운영 배포에서 기동이 막힌다. 실제로 `availability_polls.end_hour`가 `TINYINT`로 생성된 문제가 그렇게 드러났고, 로컬에서 재현하려 했을 때도 `update` 때문에 그냥 통과했다.

**AI 판정 폴러 끄기.** OpenAI 장애가 길어지면 `proof-analysis.enabled=false`(또는 `PROOF_ANALYSIS_ENABLED`)로 폴러만 내린다. 스케줄러 전체를 끄는 `todo.scheduling.enabled`와는 다르다. 다시 켜면 큐에 쌓인 건이 그대로 처리된다.

---

## 패키지 / 모듈 구조

```
com.todo
├── TodoApplication.java
├── domain/                         # 비즈니스 도메인
│   ├── auth/                       # 로그인, 회원가입, 이메일 인증, Apple 로그인, 세션(리프레시 토큰), 재인증, 비밀번호 재설정, 약관 동의 기록
│   ├── user/                       # 마이페이지, 닉네임 수정, 회원 탈퇴
│   ├── team/                       # 팀 생성/초대(메일·링크)/가입, 벌집 성장
│   ├── todo/                       # 할 일 생성/제출/반응/리포트, 작업 항목·체크인, 인증 파일 AI 판정
│   ├── availability/               # 팀 일정 조율 투표
│   ├── feed/                       # 활동 피드 집계, 뱃지
│   ├── chat/                       # 팀 채팅, 읽음 상태, 타이핑 상태
│   ├── notification/               # 사용자 알림과 미읽음 개수
│   └── terms/                      # 약관 본문 조회, 버전 확인, 동의 저장
└── global/                         # 공통/인프라
    ├── ai/                         # OpenAI Responses API 클라이언트 (도메인을 모르는 배관)
    ├── config/                     # Security, CORS, Swagger, S3, WebSocket, Apple, 스케줄링 설정
    ├── controller/                 # 파일 presigned URL, Apple Universal Links(.well-known)
    ├── dto/                        # 공통 요청/응답 DTO (파일 업로드 등)
    ├── entity/                     # BaseTimeEntity
    ├── exception/                  # BusinessException, FileStorageException, 전역 예외 처리
    ├── file/                       # 파일 삭제 outbox, 인증 문서 텍스트 추출(PDF/DOCX/XLSX/CSV)
    ├── jwt/                        # JWT 유틸리티, 필터
    ├── mail/                       # 메일 outbox, 비동기 발송
    ├── ratelimit/                  # in-memory 슬라이딩 윈도우 rate limiter, 클라이언트 IP 해석
    ├── response/                   # 공통 응답 형식 (ApiResponse)
    ├── service/                    # 파일 업로드/썸네일 (FileService)
    └── websocket/                  # STOMP CONNECT 인증 인터셉터, 팀 구독 검증, 세션 레지스트리
```

**도메인 책임 경계**

| 도메인 | 책임 |
|---|---|
| `auth` | 로그인, 회원가입, 토큰 발급/검증, Apple 로그인·탈퇴(revoke outbox), 세션 관리, 재인증, 비밀번호 재설정, 약관 동의 기록 |
| `user` | 사용자 조회/수정/탈퇴 |
| `team` | 팀과 팀원, 초대, 벌집 성장 |
| `todo` | 팀 할 일, 참가자 제출, 반응, 기간별 리포트, 작업 항목·체크인, 인증 파일 AI 판정 파이프라인 |
| `availability` | 팀 일정 조율 투표 생성/응답/집계 |
| `feed` | 활동 기록(투두 생성·제출·체크인) 집계와 뱃지 |
| `chat` | WebSocket 채팅과 읽음/타이핑 상태 |
| `notification` | 도메인 이벤트에 따른 사용자 알림 |
| `terms` | 약관 본문과 버전, 동의 조회/저장 |
| `global` | 설정, 공통 응답, 예외, JWT, WebSocket, 파일/메일 outbox, OpenAI 클라이언트, rate limit |

**외부 호출은 요청 경로에서 동기로 부르지 않는다 (outbox + 폴러 패턴).**
메일(`MailOutbox`), 파일 삭제(`FileDeletionOutbox`), Apple revoke(`AppleRevokeOutbox`), 인증 파일 AI 판정(`ProofAiAnalysis`)은 모두
트랜잭션 안에서 큐 행만 남기고, 스케줄러 폴러가 건별로 처리한다. 외부 장애가 사용자 요청 응답에 번지지 않게 하기 위한 구조이며,
새 외부 연동을 추가할 때도 같은 구조를 따른다.

---

## 코딩 컨벤션

아래 기준은 코드베이스 전반에 적용이 끝난 상태다. 신규 코드는 반드시 따르고, 기존 코드에서 어긋난 곳을 발견하면 기능 수정 시 함께 고친다.

---

#### 1. Request DTO

**규칙**
- Request DTO는 무조건 `record`로 작성
- DTO는 불변으로 유지 (setter 추가 금지)
- `@ConfigurationProperties`도 `record`를 우선한다 (`AppleProperties`, `OpenAiProperties` 패턴). `MinioProperties`만 남아 있는 `@Getter/@Setter` class 형태이며 손댈 일이 있을 때 record로 옮긴다

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

**규칙**
- 모든 Response DTO는 `record`
- Entity를 받아 DTO를 생성하는 경우 `from(Entity entity)` static factory method 필수
- 서비스 레이어에서 `new ResponseDto(field1, field2)` 직접 생성 금지
- Entity에서 파생되지 않는 응답(`LoginResponse`처럼 토큰만 담는 경우)은 `from()`이 없어도 된다

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

**규칙**
- `IllegalArgumentException`, `RuntimeException` 직접 throw 금지
- 예외 계층:
  ```
  BusinessException(message, HttpStatus)  <- 비즈니스 규칙 위반
  FileStorageException                    <- 파일 저장/읽기 실패
  AiClientException                       <- OpenAI 호출 실패 (global/ai)
  DocumentExtractionException             <- 인증 문서 텍스트 추출 실패 (global/file/extract)
  ```
- `GlobalExceptionHandler`가 `BusinessException`, 검증 실패, 업로드 크기 초과, 미지원 메서드, 미처리 예외(`Exception`)를 모두 `ApiResponse` 형식으로 응답한다
- 외부 호출 실패(`AiClientException` 등)는 호출한 도메인 서비스가 잡아 상태(`FAILED`/재시도)로 바꾼다. 폴러 밖으로 던지지 않는다

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
- 생성/수정 시각은 `BaseTimeEntity`(`global/entity`) 상속으로 JPA Auditing 적용

```java
// 현재 User 엔티티 패턴 — 표준
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task extends BaseTimeEntity {
    public static Task create(String title, User user) { ... }
    public void updateTitle(String title) { ... }  // setter 대신 의미 있는 메서드
}
```

---

#### 7. Controller 작성 규칙

- `@RestController` + `@RequiredArgsConstructor`
- Swagger 문서는 별도 `*ControllerDocs` 인터페이스로 분리
- 반환 타입: `ResponseEntity<ApiResponse<T>>`
- `@RequestMapping`으로 기본 경로 지정 (`/api/...`)
- 인프라 경로(`/.well-known/...`)처럼 클라이언트 API가 아닌 것은 Swagger 문서화에서 제외한다

---

#### 8. Service 작성 규칙

- `@Service` + `@RequiredArgsConstructor`
- 클래스 레벨: `@Transactional(readOnly = true)`
- 쓰기 작업 메서드: `@Transactional` 개별 적용
- 의존성 주입: 생성자 주입만 (`@Autowired` 필드 주입 금지)

---

#### 9. Flyway 마이그레이션 파일 규칙

새 마이그레이션은 **타임스탬프 버전**으로 작성한다. 순번(V19, V20, …)은 두 브랜치가
같은 다음 번호를 고르면 충돌해 Flyway가 기동을 거부한다 (2026-08-07 V10 중복 운영 장애).

```bash
# 생성 헬퍼 — 규칙에 맞는 파일을 만들어준다
bash scripts/new-migration.sh add_user_index
# -> src/main/resources/db/migration/V20260807223000__add_user_index.sql
```

- 형식: `V<yyyyMMddHHmmss>__<설명>.sql` (KST 기준, CI가 형식·중복·역순을 검사한다)
- 기존 순번 파일(V1~V19)은 리네임하지 않는다
- 머지가 늦어져 base에 더 새로운 마이그레이션이 먼저 들어가면 CI 역순 검사에 걸린다.
  이때는 타임스탬프를 현재 시각으로 갱신해 리네임한다
- 엔티티를 바꾸면 마이그레이션도 같이 쓴다. H2 테스트(`ddl-auto: create-drop`)는 drift를 못 잡으므로
  컬럼 타입·제약이 걸린 변경은 `./gradlew mysqlTest`로 `validate`를 통과하는지 확인한다

---

#### 10. 도메인 책임 경계

- `AuthService`: 인증/인가만 담당 (로그인, 회원가입, 토큰 발급)
  - `UserDetailsService` 구현은 현재 허용
  - 사용자 조회/수정/탈퇴 기능은 `UserService`가 담당
- 사용자 CRUD를 `AuthService`에 추가하지 않는다
- 외부 AI 호출은 `global/ai`의 `OpenAiClient`를 통해서만 한다. 클라이언트는 도메인을 모르는 배관이고,
  프롬프트(`src/main/resources/prompts/`)와 판정 해석은 호출하는 도메인 서비스(`todo/ProofAnalysisService`)가 담당한다
- AI 응답은 strict JSON 스키마(`AiStructuredRequest`)로만 받는다. 자유 텍스트 응답을 파싱하지 않는다
- 실시간 인증 변경 시 REST Security와 WebSocket CONNECT 인증을 함께 검토한다

---

## 테스트 컨벤션

**작성 의무**: 새 서비스 로직 또는 기존 비즈니스 로직 변경 시 관련 테스트를 함께 작성한다.
구현 완료의 정의에는 컴파일과 전체 테스트 통과가 포함된다.

| 계층 | 기본 전략 |
|---|---|
| Entity | 상태 전이와 불변식 단위 테스트 |
| Service | JUnit 5 + Mockito 단위 테스트 |
| Repository | 쿼리 동작이 중요할 때 `@DataJpaTest` (H2, `MODE=MySQL`) |
| Controller | 요청 검증·인증·응답 계약 중심 테스트 |
| Security/WebSocket | 필터·인터셉터의 허용/거부 경계 테스트 |
| 마이그레이션/동시성 | `@Tag("mysql")` + Testcontainers. Flyway 실제 적용, MySQL 전용 제약, REPEATABLE READ 동시성만 여기에 둔다 |

**작성 규칙**
- 현재 코드 스타일에 맞춰 동작과 기대 결과가 드러나는 한글 테스트 이름을 사용한다
- 정상 케이스와 주요 예외/경계 케이스를 각각 검증한다
- assertion 없는 테스트나 커버리지 수치만 올리는 getter/setter 테스트는 금지한다
- OpenAI, Apple, 메일, S3 의존성은 단위 테스트에서 mock으로 격리한다
- `mysql` 태그 테스트는 `./gradlew test`에서 제외되고 `./gradlew mysqlTest`로만 돈다. Docker 없이도 기본 빌드가 돌아야 한다
- 테스트 프로파일(`src/test/resources/application-test.yml`)은 `todo.scheduling.enabled=false`로 스케줄러를 끈다. 폴러는 메서드를 직접 호출해 테스트한다
- PR 변경 코드의 patch line coverage 80% 이상을 유지한다 (CI `diff-cover`)
- 전체 line coverage 85%, branch coverage 74%를 하한선으로 유지한다 (`jacocoTestCoverageVerification`)

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

**CI (`.github/workflows/ci.yml`)가 PR마다 검사하는 것**
- `test` + 전체 커버리지 게이트, `diff-cover` patch coverage 80%
- gitleaks secret-scan
- `scripts/verify-agent-harness.sh` — 커맨드 심링크, `CLAUDE.md`의 `@AGENTS.md` import, Git hook 동작
- `scripts/check-migrations.sh` — 마이그레이션 중복·역순
- Docker 이미지 빌드 (push 없음)

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
4. **민감정보 커밋 금지** — API 키(OpenAI 포함), 비밀번호, JWT 시크릿, Apple private key, DB 접속 정보 등 파일에 직접 작성 및 커밋 금지
5. **보안 변경 사람 리뷰 필수** — `SecurityConfig`, `JwtUtil`, `JwtAuthenticationFilter`, `WebSocketAuthChannelInterceptor`, `AppleIdentityTokenService` 등 보안 관련 파일 수정 시 반드시 사람이 리뷰한 후 병합
6. **커밋 전 사용자 승인 필수** — AI가 자동으로 커밋 메시지를 확정하고 커밋 실행 금지. 항상 메시지 제안 후 승인 대기
7. **AI 작업 metadata 자동 추가 금지** — 커밋, PR, merge commit에 공동 작성자 trailer, 에이전트 이름·이메일, AI 라벨을 자동으로 추가하지 않는다
8. **`application-local.yml` 읽기 금지** — `.claude/settings.json`의 deny 목록에 있다. 로컬 설정 내용이 필요하면 사용자에게 묻는다

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
