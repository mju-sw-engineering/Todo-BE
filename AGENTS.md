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
| 모듈 구조 | 단일 모듈 |

**주요 의존성**
- `spring-boot-starter-web` — REST API
- `spring-boot-starter-data-jpa` — JPA/Hibernate
- `spring-boot-starter-security` — 인증/인가
- `spring-boot-starter-validation` — 입력 검증
- `springdoc-openapi-starter-webmvc-ui:2.7.0` — Swagger UI
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

**로컬 실행 전 필요한 설정** (`src/main/resources/application-local.yml`):
- DB 접속 정보 (`spring.datasource.url`, `username`, `password`)
- JWT 시크릿 (`jwt.secret`)
- 파일 업로드 경로 (`file.upload-dir`)

---

## 패키지 / 모듈 구조

```
com.todo
├── TodoApplication.java
├── domain/                         # 비즈니스 도메인
│   ├── auth/                       # 인증 (로그인, 회원가입, JWT 발급)
│   │   ├── controller/
│   │   │   ├── AuthController.java
│   │   │   └── AuthControllerDocs.java  # Swagger 문서 전용 인터페이스
│   │   ├── service/
│   │   │   └── AuthService.java
│   │   └── dto/
│   │       ├── request/            # 요청 DTO
│   │       └── response/           # 응답 DTO
│   └── user/                       # 사용자 도메인
│       ├── entity/
│       │   └── User.java
│       └── repository/
│           └── UserRepository.java
└── global/                         # 공통/인프라
    ├── config/                     # Spring 설정 (Security, CORS, Swagger 등)
    ├── exception/                  # 전역 예외 처리
    ├── jwt/                        # JWT 유틸리티, 필터
    ├── response/                   # 공통 응답 형식 (ApiResponse)
    └── service/                    # 공통 서비스 (FileService 등)
```

**도메인 책임 경계**

| 도메인 | 책임 |
|---|---|
| `auth` | 로그인, 회원가입, 토큰 발급/검증 |
| `user` | 사용자 엔티티, 저장소 (현재는 auth에서 사용) |
| `global` | 설정, 공통 응답, 예외 핸들러, JWT 인프라 |

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

#### 9. 도메인 책임 경계 (향후 규칙)

- `AuthService`: 인증/인가만 담당 (로그인, 회원가입, 토큰 발급)
  - `UserDetailsService` 구현은 현재 허용
  - 사용자 조회/수정 기능은 `UserService`에 추가 (현재 미구현)
- 사용자 CRUD는 `UserService`로 분리 예정 — `AuthService`에 추가 금지

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

---

## 절대 규칙

다음 행동은 어떤 상황에서도 금지된다.

1. **자동 push 금지** — `git push`는 사용자가 직접 실행
2. **force push 금지** — `git push --force` 절대 실행 금지
3. **main 직접 커밋 금지** — main 브랜치에 직접 commit 금지
4. **민감정보 커밋 금지** — API 키, 비밀번호, JWT 시크릿, DB 접속 정보 등 파일에 직접 작성 및 커밋 금지
5. **보안 변경 사람 리뷰 필수** — `SecurityConfig`, `JwtUtil`, `JwtAuthenticationFilter` 등 보안 관련 파일 수정 시 반드시 사람이 리뷰한 후 병합
6. **커밋 전 사용자 승인 필수** — AI가 자동으로 커밋 메시지를 확정하고 커밋 실행 금지. 항상 메시지 제안 후 승인 대기

---

## Codex 커맨드 워크플로우

Codex가 이 레포지토리에서 작업할 때 사용자가 아래 명령을 요청하면
`.codex/commands/<command>.md` 파일을 먼저 읽고 해당 절차를 따른다.

| 명령 | 파일 | 용도 |
|---|---|---|
| `/feature` | `.codex/commands/feature.md` | 계획부터 PR 초안까지 전체 기능 워크플로우 |
| `/plan` | `.codex/commands/plan.md` | 구현 전 계획 수립 |
| `/impl` | `.codex/commands/impl.md` | 승인된 계획 기반 구현 |
| `/review` | `.codex/commands/review.md` | 변경사항 셀프 리뷰 |
| `/commit` | `.codex/commands/commit.md` | 커밋 메시지 제안 및 승인 후 커밋 |
| `/pr` | `.codex/commands/pr.md` | PR 설명 초안 작성 |

**적용 규칙**
- `.codex/commands`의 내용이 AGENTS.md와 충돌하면 AGENTS.md를 우선한다.
- 커맨드 파일을 읽었더라도 절대 규칙은 항상 유지한다.
- `/commit`은 커밋 메시지 제안 후 사용자 승인을 받은 경우에만 실행한다.
- `/pr`은 PR 초안만 작성하고, push 및 `gh pr create`는 실행하지 않는다.
