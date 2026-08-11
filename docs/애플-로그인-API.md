# Apple 로그인 API

> 프론트엔드(웹/iOS) 연동 가이드

## 흐름

```
[1단계] POST /api/auth/apple/login
          ├─ 200 { accessToken }   → 기존 회원, 끝
          └─ 202 { setupToken }    → 신규 회원, 2단계로

[2단계] POST /api/auth/apple/complete
          └─ 200 { accessToken }
```

두 응답 모두 `refreshToken`을 httpOnly 쿠키(`SameSite=Strict`, path `/api/auth`, 14일)로 내려줍니다.

---

## nonce 규칙 (가장 많이 틀리는 부분)

- **Apple에 넘길 nonce**: 원본의 **SHA-256 hex**
- **이 API에 보낼 nonce**: **원본**

서버가 받은 원본을 SHA-256 hex로 해싱해 identity token의 `nonce` 클레임과 대조합니다. 둘을 바꿔 보내면 401입니다.

---

## 1단계 — `POST /api/auth/apple/login`

```json
{
  "identityToken": "eyJ...",
  "authorizationCode": "c1a2...",
  "nonce": "원본 nonce"
}
```

| 상태 | 본문 | 의미 |
|---|---|---|
| 200 | `{ "accessToken": "..." }` | 기존 회원 로그인 완료 |
| 202 | `{ "setupToken": "..." }` | 신규 회원. 5분 안에 2단계를 마쳐야 함 |
| 401 | — | 서명·issuer·audience·nonce 검증 실패 |

`audience`는 `APPLE_IOS_CLIENT_ID`(네이티브, bundle ID)와 `APPLE_WEB_CLIENT_ID`(웹, Services ID) 둘 다 허용합니다.

---

## 2단계 — `POST /api/auth/apple/complete`

```json
{
  "setupToken": "1단계에서 받은 값",
  "nickname": "두비",
  "profileImageKey": "profiles/xxx.png",
  "termsAgreed": true,
  "privacyAgreed": true,
  "marketingAgreed": false
}
```

- `profileImageKey`는 선택. presigned 업로드로 받은 object key를 그대로 넣습니다.
- `termsAgreed`·`privacyAgreed`는 **필수로 true**. 이메일 가입과 동일하게 `user_consents`에 버전과 함께 기록됩니다.
- **이메일은 보내지 않습니다.** 서버가 1단계 identity token에서 추출해 setup token에 담아 둡니다.

| 상태 | 의미 |
|---|---|
| 200 | 가입 완료 + 로그인 |
| 400 | setup token 만료·위변조, 또는 필수 약관 미동의 |
| 409 | 이미 가입된 Apple 계정 |

### 알아둘 것

- Apple은 **이메일과 이름을 최초 인증 1회만** 줍니다. 재로그인 시에는 오지 않으므로 서버가 첫 요청에서 저장합니다.
- 그 이메일을 이미 다른 계정이 쓰고 있으면 **이메일만 비우고 가입은 진행**합니다. 여기서 막으면 그 사용자는 Apple 로그인을 영영 쓸 수 없게 되기 때문입니다.

---

## 탈퇴 — Apple 계정은 재인증 경로가 다릅니다

Apple 계정은 비밀번호가 없어 기존 `POST /api/auth/reauth`를 쓸 수 없습니다(400으로 거절).

**`POST /api/auth/reauth/apple`** (인증 필요)

```json
{
  "identityToken": "재인증용으로 새로 받은 값",
  "nonce": "원본 nonce",
  "purpose": "WITHDRAWAL"
}
```

Apple 인증 시트를 새로 통과했다는 증거를 요구합니다. 시트가 Face ID를 거치므로 비밀번호 확인보다 약하지 않습니다. 응답은 기존 재인증과 동일한 `{ reauthToken, expiresAt }`이며, 이후 `DELETE /api/users/me` 흐름은 같습니다.

로그인한 계정과 다른 Apple 계정의 토큰을 보내면 401입니다.

---

## 프로필 응답에 `provider` 추가

`GET /api/users/me`와 `GET /api/users/me/profile` 응답에 `provider`(`LOCAL` | `APPLE`)가 추가됐습니다. 탈퇴 화면에서 비밀번호를 물을지 Apple 시트를 띄울지 이 값으로 분기하세요.

`loginId`는 Apple 계정에서 **null**입니다.
