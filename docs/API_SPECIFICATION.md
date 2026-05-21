# DontDelay Server API 명세서

> Base URL: `http://localhost:8080`

| 항목 | 값 |
|------|-----|
| Total Endpoints | 3 |
| POST | 2 |
| GET | 1 |

---

## POST `/api/auth/signup`

새 사용자를 등록합니다. 중복된 username은 거부됩니다.

### Request

- **Content-Type:** `application/json`
- **인증:** 불필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| username | String | Y | 사용자명 (고유값) |
| password | String | Y | 비밀번호 (BCrypt 암호화 저장) |

```json
{
  "username": "testuser",
  "password": "mypassword123"
}
```

### Response

| Status | 조건 | 응답 Body |
|--------|------|-----------|
| 200 OK | 회원가입 성공 | `{ "message": "회원가입 성공" }` |
| 400 Bad Request | username 중복 | `{ "message": "이미 존재하는 사용자명입니다." }` |

---

## POST `/api/auth/login`

사용자 인증을 수행합니다. 성공 시 세션에 인증 정보가 저장됩니다.

### Request

- **Content-Type:** `application/json`
- **인증:** 불필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| username | String | Y | 등록된 사용자명 |
| password | String | Y | 비밀번호 |

```json
{
  "username": "testuser",
  "password": "mypassword123"
}
```

### Response

| Status | 조건 | 응답 Body |
|--------|------|-----------|
| 200 OK | 로그인 성공 | `{ "message": "로그인 성공", "username": "testuser" }` |
| 401 Unauthorized | 인증 실패 | Spring Security 기본 에러 응답 |

---

## GET `/api/health`

서버 상태를 확인합니다. 커스텀 헬스체크 엔드포인트입니다.

### Request

- **인증:** 불필요
- **파라미터:** 없음

### Response

| Status | 응답 Body |
|--------|-----------|
| 200 OK | `{ "status": "UP", "timestamp": "2026-05-21T13:30:00" }` |

| 필드 | 타입 | 설명 |
|------|------|------|
| status | String | 서버 상태 ("UP") |
| timestamp | String | 응답 시각 (ISO 8601) |

---

## GET `/actuator/health`

Spring Boot Actuator 헬스체크입니다. DB 연결 상태 등 상세 정보를 포함합니다.

### Response 예시

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "H2",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

---

## 인증 정책

| 경로 패턴 | 접근 권한 |
|-----------|----------|
| `/api/auth/**` | 누구나 접근 가능 |
| `/actuator/health` | 누구나 접근 가능 |
| `/h2-console/**` | 누구나 접근 가능 |
| 그 외 모든 경로 | 인증 필요 (로그인 세션) |

- CSRF 보호는 비활성화 상태입니다.
- 비밀번호는 BCrypt로 암호화됩니다.
