# 투두 조회 API 변경사항 안내

> 2026-07-15 · 중복 API 통합 리팩토링
> 프론트엔드(웹/iOS) 개발자용 마이그레이션 가이드

## 요약

투두 목록 조회 API 3개가 사실상 같은 기능이라 **1개로 통합**되었습니다.
기존 API는 당분간 그대로 동작하지만(하위 호환), **deprecated 상태**이므로 새 방식으로 전환해 주세요.

| 기존 | 상태 | 새 방식 |
|---|---|---|
| `GET /api/teams/{teamId}/todos` | ✅ 유지 (기능 확장) | 그대로 사용 |
| `GET /api/teams/{teamId}/todos/today` | ⚠️ Deprecated | `GET /todos?date=오늘날짜` |
| `GET /api/teams/{teamId}/todos/history?date=` | ⚠️ Deprecated | `GET /todos?date=날짜` |
| `GET /api/teams/{teamId}/todos/report` | ✅ 변경 없음 | 그대로 사용 |

---

## 통합 API 사용법

### `GET /api/teams/{teamId}/todos`

| 호출 | 동작 |
|---|---|
| `GET /todos` | **전체 투두 목록** (기존과 동일) |
| `GET /todos?filter=IN_PROGRESS` | 진행 중인 투두만 (기존과 동일) |
| `GET /todos?filter=ENDED` | 종료된(성공/실패) 투두만 (기존과 동일) |
| `GET /todos?date=2026-07-15` | **[신규]** 해당 날짜 마감 투두 (today/history 대체) |

### 주의사항

1. **`filter`와 `date`는 동시에 사용할 수 없습니다** → 같이 보내면 `400` 에러
   ```
   GET /todos?filter=ENDED&date=2026-07-15   ❌ 400 "filter와 date는 함께 사용할 수 없습니다."
   ```

2. **`date`는 `yyyy-MM-dd` 형식, Asia/Seoul(KST) 기준**입니다.
   "오늘 투두"가 필요하면 **클라이언트가 KST 기준 오늘 날짜를 계산해서** 보내야 합니다.
   ```swift
   // iOS 예시
   var calendar = Calendar.current
   calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
   ```
   ```javascript
   // 웹 예시
   new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' })  // "2026-07-15"
   ```

3. **빈 목록 메시지가 변경**되었습니다:
   | 호출 | 결과가 없을 때 메시지 |
   |---|---|
   | `GET /todos` (파라미터 없음/filter) | `"조회된 할 일이 없습니다"` ← 기존 "오늘 할 일이 없습니다"에서 변경 |
   | `GET /todos?date=...` | `"해당 날짜의 할 일이 없습니다"` |

   응답 메시지 문자열을 직접 비교하는 코드가 있다면 확인 필요합니다.

---

## 마이그레이션 예시

### 오늘 투두 조회
```
기존:  GET /api/teams/1/todos/today
변경:  GET /api/teams/1/todos?date=2026-07-15   (클라이언트가 KST 오늘 날짜 계산)
```

### 특정 날짜 투두 조회
```
기존:  GET /api/teams/1/todos/history?date=2026-07-01
변경:  GET /api/teams/1/todos?date=2026-07-01
```

응답 형태(`TodoSummaryResponse` 배열)는 **완전히 동일**합니다. URL만 바꾸면 됩니다.

---

## 변경 배경

- `/todos`, `/todos/today`, `/todos/history` 세 API가 모두 같은 응답 DTO를 반환하고, today/history는 내부적으로 동일한 쿼리를 하루 범위로 실행하는 중복 API였음
- 백엔드 내부 로직은 하나로 통합 완료, 기존 엔드포인트는 통합 로직에 위임하는 래퍼로 유지 중
- AI 챗봇(Todo-AI)은 이미 새 통합 API로 전환 완료

## 삭제 예정 일정

웹/iOS 모두 새 방식으로 전환이 확인되면 `/todos/today`, `/todos/history` 엔드포인트를 삭제할 예정입니다.
**전환 완료 시 백엔드 팀에 알려주세요.**
