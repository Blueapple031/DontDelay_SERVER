# DontDelay Server API 명세서

> Base URL: `http://localhost:8080`

| 항목 | 값 |
|------|-----|
| Total Endpoints | 6 |
| POST | 3 |
| GET | 3 |

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
| realName | String | Y | 실명 |
| email | String | Y | 이메일 (고유값, 이메일 형식) |
| department | String | Y | 학과 |

```json
{
  "username": "testuser",
  "password": "mypassword123",
  "realName": "홍길동",
  "email": "hong@example.com",
  "department": "컴퓨터공학과"
}
```

### Response

| Status | 조건 | 응답 Body |
|--------|------|-----------|
| 200 OK | 회원가입 성공 | `{ "message": "회원가입 성공" }` |
| 400 Bad Request | username 중복 | `{ "message": "이미 존재하는 사용자명입니다." }` |
| 400 Bad Request | email 중복 | `{ "message": "이미 등록된 이메일입니다." }` |
| 400 Bad Request | 필수값·형식 오류 | Spring Validation 기본 에러 응답 |

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
| 200 OK | 로그인 성공 | 아래 예시 |

```json
{
  "message": "로그인 성공",
  "username": "testuser",
  "realName": "홍길동",
  "email": "hong@example.com",
  "department": "컴퓨터공학과",
  "major": "컴퓨터공학과"
}
```
| 401 Unauthorized | 인증 실패 | Spring Security 기본 에러 응답 |

---

## GET `/api/auth/me`

로그인 세션 기준으로 현재 사용자 프로필을 조회합니다.

### Request

- **인증:** 필요 (로그인 세션 쿠키)

### Response

| Status | 조건 | 응답 Body |
|--------|------|-----------|
| 200 OK | 조회 성공 | 아래 예시 |
| 401 Unauthorized | 미로그인·사용자 없음 | `{ "error": "UNAUTHORIZED", "message": "..." }` |

```json
{
  "username": "testuser",
  "realName": "홍길동",
  "email": "hong@example.com",
  "department": "컴퓨터공학과",
  "major": "컴퓨터공학과"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| username | String | 사용자명 |
| realName | String | 실명 |
| email | String | 이메일 |
| department | String | 학과 |
| major | String | 전공 (`department`와 동일) |

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

## Exam Generator (`/api/exam/documents`)

세션 인증 필요. 상세 명세는 [`PLAN_EXAM_GENERATOR.md`](PLAN_EXAM_GENERATOR.md) 참고.

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/exam/documents` | PDF 업로드 (multipart: `file`, `title?`, `subject?`) → `201` |
| GET | `/api/exam/documents` | 문서 목록 (`status?`, `page`, `size`) |
| GET | `/api/exam/documents/{documentId}` | 문서 상세·진행률 |

로컬 개발 시 `exam.storage.type=local` (기본값). 운영 S3는 `exam.storage.type=s3` 및 AWS 자격 증명 설정.

---

## 인증 정책

| 경로 패턴 | 접근 권한 |
|-----------|----------|
| `/api/auth/**` | 누구나 접근 가능 |
| `/actuator/health` | 누구나 접근 가능 |
| `/h2-console/**` | 누구나 접근 가능 |
| `/api/exam/**` | 인증 필요 (로그인 세션) |
| 그 외 모든 경로 | 인증 필요 (로그인 세션) |

- CSRF 보호는 비활성화 상태입니다.
- 비밀번호는 BCrypt로 암호화됩니다.
