# /review - 변경사항 셀프 리뷰

## 역할
구현된 변경사항을 AGENTS.md 기준으로 셀프 리뷰한다.
FAIL 항목은 수정안을 제시하고 사용자 승인을 받는다.

## 실행 순서

### 1. 변경사항 파악
```bash
git diff HEAD
git diff --cached
```

두 명령 모두 결과가 비어있으면 즉시 다음 메시지를 출력하고 종료한다:
```
리뷰할 변경사항이 없습니다.
```

### 2. 체크리스트 항목별 검토

각 항목에 대해 **PASS / FAIL / N/A** 와 근거를 작성한다.

---

**[컨벤션]**
- [ ] 신규 Request DTO가 `record`로 작성됐는가
- [ ] 신규 Response DTO가 `record` + `from(Entity)` 패턴을 따르는가
- [ ] Validation 어노테이션이 별도 줄에 배치됐는가
- [ ] `IllegalArgumentException`, `RuntimeException` 직접 throw가 없는가
- [ ] Swagger `@ApiResponse` FQN 규칙을 준수했는가
- [ ] Entity에 setter가 추가되지 않았는가
- [ ] Controller가 `ResponseEntity<ApiResponse<T>>` 형식을 반환하는가
- [ ] Service에 `@Transactional(readOnly = true)` + 쓰기 메서드 `@Transactional` 패턴을 따르는가

**[N+1 쿼리]**
- [ ] 연관 엔티티 조회 시 `fetch join` 또는 `@EntityGraph` 적용 여부 확인
- [ ] 반복문 내부에서 repository 메서드를 호출하는 코드가 없는가

**[트랜잭션 경계]**
- [ ] 여러 저장소 작업이 하나의 트랜잭션 내에 있는가
- [ ] `@Transactional` 메서드에서 예외가 발생해도 롤백이 보장되는가
- [ ] 파일 I/O 등 트랜잭션 대상이 아닌 작업이 트랜잭션 메서드 안에 혼재하지 않는가

**[예외 처리]**
- [ ] 발생 가능한 예외가 모두 처리됐는가
- [ ] `GlobalExceptionHandler`에서 처리되지 않는 새로운 예외 타입이 없는가

**[테스트 커버리지]**
- [ ] 핵심 비즈니스 로직에 대한 테스트가 작성됐는가
- [ ] 경계값, 예외 케이스에 대한 테스트가 있는가

**[DTO / Entity 경계]**
- [ ] Entity가 Controller 레이어까지 노출되지 않는가
- [ ] Service가 DTO를 Entity로, Entity를 DTO로 변환하는 책임을 갖는가
- [ ] Response DTO의 `from()` 안에서만 Entity 필드를 직접 참조하는가

**[보안]**
- [ ] 인증이 필요한 엔드포인트에 `SecurityConfig`의 인가 설정이 추가됐는가
- [ ] 민감정보(비밀번호, 토큰)가 Response DTO에 포함되지 않는가
- [ ] 보안 관련 파일(SecurityConfig, JwtUtil, JwtAuthenticationFilter)이 수정됐다면 리뷰 필요 명시

**[코드 정리]**
- [ ] 사용하지 않는 import가 없는가
- [ ] 주석 처리된 코드가 없는가
- [ ] TODO 주석이 남아있다면 명시

---

### 3. 결과 보고

**FAIL 항목 처리**
- FAIL인 항목에 대해 구체적인 수정 코드 제안
- 사용자 승인 후 수정 진행

**보고 형식**
```
[컨벤션] PASS
[N+1 쿼리] PASS
[트랜잭션 경계] N/A - 단순 조회만 포함
[예외 처리] FAIL - FileService에서 RuntimeException 직접 사용. 수정안: ...
...

총 FAIL: X건 - 수정안 검토 후 승인 부탁드립니다.
```

## 주의사항
- 코드를 직접 수정하지 않는다. 리뷰 -> 승인 -> 수정 순서를 지킨다
- PASS로 처리하기 애매한 항목은 FAIL로 처리하고 사용자에게 판단을 맡긴다
