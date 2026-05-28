# 시험 문제 생성기 — PDF 업로드 · RAG · LaTeX PDF 계획서

## 1. 목표

- 사용자가 **학습 PDF**를 업로드하면, 서버가 텍스트를 추출·인덱싱하고 **RAG 기반으로 시험 문제**를 생성한다.
- 생성 결과는 **구조화 JSON(문항 목록)** + **LaTeX 컴파일 PDF**로 제공한다.
- **LLM·임베딩·OCR·S3·pgvector**는 모두 **Spring 백엔드**(`http://dontdelay.duckdns.org:8080`)에서 처리한다. Flutter 앱에는 비밀키를 넣지 않는다.
- 기존 **세션 로그인**(`dio` + `CookieManager`, `/api/auth/login`)과 동일한 인증 체계로 API를 보호한다.
- Flutter **`exam_generator`** 화면에서 업로드·처리 상태·문제 생성·PDF 다운로드를 제공한다.
- **2차 연동:** `exam_mode`(시험기간 모드), `ai_coach`(학습 코칭)에 생성된 문서·시험 결과를 컨텍스트로 전달한다.

---

## 2. 범위 및 비범위

| 포함 | 제외(이번 단계) |
|------|-----------------|
| PDF 업로드 → S3 저장 → DB 메타데이터 | Word(.docx), 이미지 단독 업로드 |
| PDFBox 텍스트 추출 + (필요 시) OCR fallback | 수식 OCR 고정밀(수학 전용 엔진) |
| chunking + 임베딩 + **pgvector** 저장 | Elasticsearch / Pinecone 등 외부 벡터 DB |
| RAG 유사도 검색 API | 하이브리드 검색(BM25 + vector) |
| LLM 구조화 JSON 문제 생성 | 실시간 채팅형 문제 생성 UI |
| LaTeX → PDF 컴파일 후 S3 저장 | 앱 내 LaTeX 렌더·수식 에디터 |
| Job 기반 **비동기 처리** + 상태 폴링 | WebSocket/SSE 실시간 진행률 |
| Flutter: 문서 목록·업로드·생성 폼·다운로드 | 오프라인 모드·로컬 PDF 편집 |
| 사용자별 문서·시험 데이터 격리 | 문서 공유·팀 협업 |
| Rate limit·파일 크기 상한·에러 코드 | 다국어(i18n) |
| exam_mode·ai_coach 컨텍스트 연동 (Phase 6) | 자동 채점·OMR 스캔 |

### 2.1 파일 저장 — AWS S3 (확정)

| 항목 | 결정 |
|------|------|
| **저장소** | **AWS S3** 단일 사용 (서버 로컬 디스크·GCS·Notion 원본 저장은 사용하지 않음) |
| **버킷** | private, 리전 `ap-northeast-2`(서울) |
| **업로드 경로** | Flutter → Spring `multipart` → Spring이 S3 `PutObject` (클라이언트 presigned upload는 2차) |
| **다운로드** | Spring `GET .../download` → **presigned URL**(15분) redirect 또는 proxy stream |
| **DB** | 바이너리 미저장. `s3_key`·`pdf_s3_key`만 저장 |
| **Spring SDK** | AWS SDK for Java v2 (`software.amazon.awssdk:s3`) |

**S3를 선택한 이유:** Spring 서버와 분리된 내구성·백업, presigned URL로 Flutter 다운로드 단순화, VM 디스크 부담 없음, pgvector(Postgres)와 역할 분리(메타·벡터는 DB, 파일은 S3).

---

## 3. 전체 아키텍처

```text
┌──────────────────────────────────────────────────────────────────────────┐
│  DontDelay (Flutter Desktop)                                             │
│  ┌──────────────────┐   ┌─────────────────────┐   ┌──────────────────┐ │
│  │ exam_generator   │◀──│ exam_generator_     │◀──│ exam_generator_  │ │
│  │ .dart (UI)       │   │ provider (Riverpod) │   │ service (Dio)    │ │
│  └────────┬─────────┘   └──────────┬──────────┘   └────────┬─────────┘ │
│           │                        │                          │           │
│  ┌────────┴────────────────────────┴──────────────────────────┘           │
│  │ authProvider → 로그인 여부                                             │
│  │ examDioProvider → 업로드·장시간 Job용 타임아웃 (120초+)                 │
│  │ (Phase 6) exam_mode / ai_coach ← 생성 문서·시험 메타 참조              │
│  └────────────────────────────────────────────────────────────────────────┘
└───────────────────────────────┬──────────────────────────────────────────┘
                                │ HTTP + Cookie: JSESSIONID
                                │ multipart upload / JSON / PDF download
                                ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Spring Boot (dontdelay.duckdns.org:8080)                                │
│  ┌──────────────┐   ┌──────────────────┐   ┌─────────────────────────┐  │
│  │ Security     │──▶│ ExamDocument     │──▶│ S3 (원본 PDF, 결과 PDF) │  │
│  │ (세션 인증)  │   │ ExamJob          │   └─────────────────────────┘  │
│  └──────────────┘   │ Controller       │                                  │
│                     └────────┬─────────┘                                  │
│                              │                                            │
│         ┌────────────────────┼────────────────────┐                       │
│         ▼                    ▼                    ▼                       │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────┐              │
│  │ Extraction  │    │ Chunk +      │    │ RagService      │              │
│  │ PDFBox/OCR  │───▶│ Embedding    │───▶│ (pgvector)      │              │
│  └─────────────┘    └──────────────┘    └────────┬────────┘              │
│                                                     │                       │
│                              ┌──────────────────────▼──────────────┐       │
│                              │ ExamGenerationService               │       │
│                              │  RAG context → LlmClient → JSON     │       │
│                              │  → LatexPdfService → S3             │       │
│                              └─────────────────────────────────────┘       │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ PostgreSQL + pgvector                                             │    │
│  │ study_document | document_chunk | exam_generation_job |           │    │
│  │ generated_exam                                                    │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│  (공유) LlmClient, EmbeddingClient — com.dontdelay.ai 와 재사용           │
└──────────────────────────────────────────────────────────────────────────┘
```

**동작 요약**

1. 사용자가 로그인 후 `exam_generator` 화면에서 PDF 선택·업로드.
2. Spring이 S3에 원본 저장, `study_document` 레코드 생성, **비동기 Job**으로 추출·chunk·임베딩 시작.
3. 앱이 `GET .../status`로 `EXTRACTING → INDEXING → READY` 폴링.
4. `READY` 상태에서 문항 수·유형·난이도 입력 후 `POST /api/exam/jobs` → RAG + LLM + LaTeX PDF 생성.
5. `GET /api/exam/jobs/{jobId}`로 `COMPLETED` 확인 후 PDF 다운로드.
6. (Phase 6) `exam_mode`·`ai_coach`가 최근 문서·시험 메타를 컨텍스트에 포함.

---

## 4. Job 상태 모델

문서 처리와 시험 생성은 **별도 Job**으로 관리한다.

### 4.1 문서 인덱싱 상태 (`DocumentStatus`)

| 상태 | 설명 |
|------|------|
| `UPLOADED` | S3 업로드 완료, 추출 대기 |
| `EXTRACTING` | PDFBox/OCR 텍스트 추출 중 |
| `INDEXING` | chunking + embedding + pgvector 저장 중 |
| `READY` | RAG 검색·문제 생성 가능 |
| `FAILED` | 처리 실패 (`errorCode` 참조) |

### 4.2 시험 생성 Job 상태 (`ExamJobStatus`)

| 상태 | 설명 |
|------|------|
| `PENDING` | Job 큐 등록 |
| `GENERATING` | RAG + LLM 문제 생성 중 |
| `RENDERING` | LaTeX PDF 컴파일 중 |
| `COMPLETED` | JSON + PDF 준비 완료 |
| `FAILED` | 생성 실패 |

### 4.3 상태 전이

```text
[업로드] → UPLOADED → EXTRACTING → INDEXING → READY
                                    ↘ FAILED

[생성 요청] → PENDING → GENERATING → RENDERING → COMPLETED
                                      ↘ FAILED
```

---

## 5. 인증·보안

| 항목 | 정책 |
|------|------|
| 인증 | `POST /api/auth/login` 후 **세션 쿠키**. `/api/exam/**`는 `authenticated` 필요. |
| Flutter | [`auth_provider.dart`](../lib/features/auth/auth_provider.dart)의 `dioProvider` 재사용. 업로드·Job용 `examDioProvider` 분리(타임아웃 상향). |
| API 키 | `OPENAI_API_KEY`, `GEMINI_API_KEY`, `AWS_ACCESS_KEY_ID` 등 **환경변수만**. Git·앱 바이너리 금지. |
| 데이터 격리 | 모든 조회·삭제는 **로그인 user_id** 기준. 타 사용자 documentId/jobId → `404`. |
| 파일 검증 | MIME `application/pdf`, 확장자 `.pdf`, 최대 **50MB**(설정 가능). |
| 미로그인 | `401` → 앱에서 로그인 유도. |
| Rate limit | 업로드: 사용자당 **시간당 10건**. 문제 생성: **일 20건**(예시, env로 조정). |
| S3 | **private 버킷**. Block Public Access 전체 ON. 다운로드는 **presigned GET URL**(기본 15분). IAM user/role은 `PutObject`·`GetObject`·`DeleteObject`만 버킷 prefix에 허용. |
| 로깅 | PDF 원문·chunk 전문은 운영 로그에 남기지 않음. Job ID·user_id·status만 감사 로그. |

---

## 6. 백엔드 API 명세

Base URL: `http://dontdelay.duckdns.org:8080` (운영 시 HTTPS 권장)

### 6.1 `POST /api/exam/documents`

**설명:** PDF 파일을 업로드하고 S3·DB에 등록한다. 업로드 직후 비동기 인덱싱 Job이 시작된다.

**인증:** 필수

**Request:** `multipart/form-data`

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `file` | file | O | PDF 파일 |
| `title` | string | X | 표시명 (없으면 파일명) |
| `subject` | string | X | 과목명 (예: "선형대수") |

**Response 201**

```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "title": "3장_행렬식.pdf",
  "subject": "선형대수",
  "status": "UPLOADED",
  "fileSizeBytes": 2048576,
  "createdAt": "2026-05-28T10:00:00+09:00"
}
```

---

### 6.2 `GET /api/exam/documents`

**설명:** 현재 사용자의 업로드 문서 목록.

**Query:** `status` (선택), `page` (기본 0), `size` (기본 20)

**Response 200**

```json
{
  "items": [
    {
      "documentId": "550e8400-e29b-41d4-a716-446655440000",
      "title": "3장_행렬식.pdf",
      "subject": "선형대수",
      "status": "READY",
      "pageCount": 24,
      "chunkCount": 48,
      "createdAt": "2026-05-28T10:00:00+09:00",
      "updatedAt": "2026-05-28T10:03:12+09:00"
    }
  ],
  "total": 1
}
```

---

### 6.3 `GET /api/exam/documents/{documentId}`

**설명:** 문서 상세 + 처리 진행률.

**Response 200**

```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "title": "3장_행렬식.pdf",
  "subject": "선형대수",
  "status": "INDEXING",
  "progress": 65,
  "pageCount": 24,
  "chunkCount": 31,
  "errorCode": null,
  "errorMessage": null,
  "createdAt": "2026-05-28T10:00:00+09:00",
  "updatedAt": "2026-05-28T10:02:30+09:00"
}
```

| 필드 | 설명 |
|------|------|
| `progress` | 0~100. `READY`/`FAILED` 시 100 또는 0 |
| `chunkCount` | `INDEXING` 중 증가, `READY`에서 최종값 |

---

### 6.4 `DELETE /api/exam/documents/{documentId}` (선택, Phase 2)

**설명:** S3 원본·chunk·관련 Job soft delete 또는 cascade delete.

**Response:** `204 No Content`

---

### 6.5 `POST /api/exam/documents/{documentId}/reindex` (선택, Phase 2)

**설명:** `FAILED` 또는 품질 재처리 시 인덱싱 Job 재시작.

**Response 202:** `{ "documentId", "status": "UPLOADED" }`

---

### 6.6 `POST /api/exam/documents/{documentId}/search` (Phase 3, 디버그·ai_coach용)

**설명:** RAG 유사도 검색. 문제 생성 파이프라인 내부에서도 사용.

**Request Body**

```json
{
  "query": "행렬식의 정의와 성질",
  "topK": 5,
  "minScore": 0.7
}
```

**Response 200**

```json
{
  "chunks": [
    {
      "chunkId": "uuid",
      "content": "행렬식 det(A)는 ...",
      "pageNo": 12,
      "score": 0.89
    }
  ]
}
```

---

### 6.7 `POST /api/exam/jobs`

**설명:** `READY` 문서 기준으로 시험 문제 생성 Job을 등록한다.

**Request Body**

```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "options": {
    "questionCount": 10,
    "types": ["multiple_choice", "short_answer"],
    "difficulty": "medium",
    "subject": "선형대수",
    "includeExplanation": true
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `documentId` | string | O | `READY` 상태 문서 |
| `options.questionCount` | int | O | 1~30 |
| `options.types` | array | O | `multiple_choice`, `short_answer`, `true_false` |
| `options.difficulty` | string | X | `easy` \| `medium` \| `hard` (기본 `medium`) |
| `options.subject` | string | X | PDF 메타보다 우선 |
| `options.includeExplanation` | bool | X | 기본 `true` |

**Response 202**

```json
{
  "jobId": "660e8400-e29b-41d4-a716-446655440001",
  "status": "PENDING",
  "createdAt": "2026-05-28T11:00:00+09:00"
}
```

---

### 6.8 `GET /api/exam/jobs/{jobId}`

**설명:** 생성 Job 상태·결과 메타.

**Response 200 (진행 중)**

```json
{
  "jobId": "660e8400-e29b-41d4-a716-446655440001",
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "GENERATING",
  "progress": 40,
  "examId": null,
  "errorCode": null,
  "createdAt": "2026-05-28T11:00:00+09:00",
  "updatedAt": "2026-05-28T11:00:15+09:00"
}
```

**Response 200 (완료)**

```json
{
  "jobId": "660e8400-e29b-41d4-a716-446655440001",
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "progress": 100,
  "examId": "770e8400-e29b-41d4-a716-446655440002",
  "exam": {
    "title": "선형대수 — 3장 행렬식 연습",
    "questionCount": 10,
    "downloadUrl": "/api/exam/exams/770e8400.../download",
    "previewQuestions": [
      {
        "number": 1,
        "type": "multiple_choice",
        "stem": "다음 중 2×2 행렬 A의 행렬식은?",
        "choices": ["A. ...", "B. ...", "C. ...", "D. ..."]
      }
    ]
  },
  "createdAt": "2026-05-28T11:00:00+09:00",
  "updatedAt": "2026-05-28T11:01:30+09:00"
}
```

> **MVP:** `previewQuestions`는 1~2문항만. 전체 JSON은 `GET /api/exam/exams/{examId}`.

---

### 6.9 `GET /api/exam/exams/{examId}`

**설명:** 생성된 시험 전체 JSON (정답·해설 포함).

**Response 200**

```json
{
  "examId": "770e8400-e29b-41d4-a716-446655440002",
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "title": "선형대수 — 3장 행렬식 연습",
  "subject": "선형대수",
  "difficulty": "medium",
  "questions": [
    {
      "number": 1,
      "type": "multiple_choice",
      "stem": "다음 중 ...",
      "choices": ["A. ...", "B. ...", "C. ...", "D. ..."],
      "answer": "B",
      "explanation": "행렬식은 ...",
      "sourceChunkIds": ["chunk-uuid-1"]
    }
  ],
  "createdAt": "2026-05-28T11:01:30+09:00"
}
```

---

### 6.10 `GET /api/exam/exams/{examId}/download`

**설명:** LaTeX 컴파일 PDF 다운로드.

**Response 200:** `Content-Type: application/pdf`, `Content-Disposition: attachment`

또는 presigned URL redirect (설정에 따름).

---

### 6.11 `GET /api/exam/jobs` (선택)

**설명:** 사용자의 최근 생성 Job 목록 (Flutter 히스토리용).

---

### Error Responses (공통)

| HTTP | code | 의미 | 앱 처리 |
|------|------|------|---------|
| 400 | `INVALID_FILE` | PDF 아님·크기 초과 | 스낵바 |
| 400 | `INVALID_OPTIONS` | questionCount 범위 초과 | 폼 검증 |
| 401 | — | 미로그인 | 로그인 유도 |
| 404 | `NOT_FOUND` | document/job/exam 없음 | 목록 새로고침 |
| 409 | `DOCUMENT_NOT_READY` | `READY` 아닌 문서로 생성 요청 | 상태 안내 |
| 429 | `RATE_LIMITED` | 업로드·생성 한도 초과 | “잠시 후 재시도” |
| 502 | `LLM_UNAVAILABLE` | LLM 업스트림 실패 | 재시도 버튼 |
| 502 | `LATEX_FAILED` | LaTeX 컴파일 실패 | JSON만 표시 fallback |
| 503 | `EXAM_DISABLED` | 기능 off | 안내 문구 |

**Error Body 예시**

```json
{
  "error": "DOCUMENT_NOT_READY",
  "message": "문서 인덱싱이 완료된 후 문제를 생성할 수 있습니다.",
  "details": { "status": "INDEXING", "progress": 45 }
}
```

---

## 7. 백엔드 구현 설계 (Spring)

### 7.1 패키지 구조 (권장)

```text
com.dontdelay.exam
├── controller   ExamDocumentController, ExamJobController, ExamController
├── dto          UploadResponse, DocumentDto, SearchRequest, JobRequest, ...
├── domain       StudyDocument, DocumentChunk, ExamGenerationJob, GeneratedExam, Question
├── repository   StudyDocumentRepository, DocumentChunkRepository, ...
├── service      DocumentService, ExtractionService, ChunkService,
│                EmbeddingService, RagService, ExamGenerationService, LatexPdfService
├── job          DocumentIndexingJob, ExamGenerationAsyncJob
└── infra        StorageClient (interface), S3StorageClient, PdfBoxExtractor, OcrClient (interface)

com.dontdelay.ai          ← AI 코치와 공유
├── service      LlmClient (interface), EmbeddingClient (interface)
└── infra        OpenAiLlmClient, OpenAiEmbeddingClient
```

### 7.2 S3 설정 및 경로 규칙

#### 버킷·IAM (Phase 0)

| 항목 | 값 |
|------|-----|
| 버킷명 | `dontdelay-exam` (env: `AWS_S3_BUCKET`) |
| 리전 | `ap-northeast-2` |
| 버전 관리 | (선택) 활성화 — 실수 삭제 복구 |
| 수명 주기 | (선택) `FAILED` 문서 prefix 90일 후 Glacier/삭제 |

**IAM 정책 예시 (Spring 전용 user/role)**

```json
{
  "Effect": "Allow",
  "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
  "Resource": "arn:aws:s3:::dontdelay-exam/users/*"
}
```

#### 객체 키 패턴

| 용도 | 키 패턴 |
|------|---------|
| 원본 PDF | `users/{userId}/documents/{documentId}/original.pdf` |
| 생성 PDF | `users/{userId}/exams/{examId}/exam.pdf` |
| (선택) 추출 텍스트 | `users/{userId}/documents/{documentId}/extracted.txt` |

- `Content-Type`: 업로드 시 `application/pdf` 설정.
- `ServerSideEncryption`: `AES256` (또는 SSE-KMS, 2차).

#### `StorageClient` / `S3StorageClient`

```java
public interface StorageClient {
  void put(String key, InputStream body, long sizeBytes, String contentType);
  InputStream get(String key);
  void delete(String key);
  URL presignedGetUrl(String key, Duration ttl);
}
```

| 메서드 | 호출처 |
|--------|--------|
| `put` | `DocumentService` 업로드, `LatexPdfService` 결과 PDF |
| `get` | `ExtractionService` PDFBox/OCR 입력 |
| `delete` | `DELETE /api/exam/documents/{id}` |
| `presignedGetUrl` | `GET /api/exam/exams/{id}/download` |

**업로드 흐름 (MVP)**

1. Flutter `POST /api/exam/documents` (multipart).
2. Spring이 파일 검증 → UUID `documentId` 발급 → S3 `put`.
3. DB `study_document` insert (`s3_key`, status=`UPLOADED`).
4. `@Async` 인덱싱 Job 시작.

> **2차 옵션:** 대용량(50MB+) 시 Spring이 presigned **PUT** URL 발급 → Flutter가 S3 직접 업로드 → Spring에 완료 통지. MVP는 Spring 경유 업로드로 충분.

### 7.3 텍스트 추출 (`ExtractionService`)

1. S3에서 PDF 스트림 다운로드.
2. **PDFBox**로 페이지별 텍스트 추출.
3. **OCR 분기:** 전체 문자 수 / (페이지 수 × 임계값) 비율이 낮으면 OCR (Tesseract 또는 클라우드 Vision).
4. 페이지 번호 메타와 함께 plain text 반환.

| errorCode | 조건 |
|-----------|------|
| `EMPTY_PDF` | 추출 텍스트 0자 |
| `OCR_FAILED` | OCR 시도 후에도 실패 |
| `ENCRYPTED_PDF` | 암호화 PDF |

### 7.4 Chunking (`ChunkService`)

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `chunkSize` | 800자 | 토큰 대신 문자 기준 (MVP) |
| `chunkOverlap` | 120자 | 문맥 연속성 |
| `minChunkSize` | 100자 | 너무 짧은 조각 병합 |

- chunk마다 `document_id`, `chunk_index`, `page_no`, `content` 저장.
- Phase 4+: 문단·헤딩 기준 semantic chunking 검토.

### 7.5 Embedding + pgvector

```sql
CREATE EXTENSION IF NOT EXISTS vector;

-- document_chunk.embedding: vector(1536) — OpenAI text-embedding-3-small 기준
CREATE INDEX ON document_chunk
  USING hnsw (embedding vector_cosine_ops);
```

- `EmbeddingService.embed(List<String> texts)` → batch API 호출.
- 유사도: `ORDER BY embedding <=> :queryEmbedding LIMIT :topK`.

### 7.6 RAG (`RagService`)

**문제 생성 시 컨텍스트 수집 전략 (MVP):**

1. options.subject + difficulty로 **검색 쿼리 N개** LLM 또는 템플릿 생성 (N=3~5).
2. 각 쿼리로 topK chunk 수집 → **중복 제거** → 최대 M개(M=15) 컨텍스트 블록.
3. `PromptBuilder`(exam 전용)에 `[page N] content` 형식으로 삽입.

### 7.7 문제 생성 (`ExamGenerationService`)

- `LlmClient` + **structured JSON** (`response_format: json_object`).
- 시스템 프롬프트: 제공된 chunk만 근거로 출제, 환각 금지, `sourceChunkIds` 필수.
- 출력 스키마: `questions[]` (stem, type, choices?, answer, explanation, sourceChunkIds).
- 파싱 실패 시 1회 재시도 → 실패 시 `LLM_UNAVAILABLE`.

### 7.8 LaTeX PDF (`LatexPdfService`)

1. `resources/latex/exam_template.tex` + 문항 loop.
2. 임시 디렉터리에 `.tex` 작성 → **`pdflatex`** 2회 실행 (목차·참조).
3. 한글: `kotex` 또는 `xelatex` + `Noto Sans CJK` (서버 폰트 설치 필요).
4. 성공 PDF → S3 업로드. 실패 시 `LATEX_FAILED`, JSON 결과는 유지.

### 7.9 Security 설정

```java
.requestMatchers("/api/exam/**").authenticated()
```

- Service 레이어에서 `document.userId == currentUserId` 검증.

### 7.10 DB 스키마 (JPA 예시)

**`study_document`**

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | UUID PK | `documentId` |
| `user_id` | FK | 로그인 사용자 |
| `title` | varchar | |
| `subject` | varchar nullable | |
| `s3_key` | varchar | 원본 PDF |
| `status` | varchar | DocumentStatus enum |
| `progress` | int | 0~100 |
| `page_count` | int nullable | |
| `chunk_count` | int default 0 | |
| `error_code` | varchar nullable | |
| `error_message` | text nullable | |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

**`document_chunk`**

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | UUID PK | |
| `document_id` | FK | |
| `chunk_index` | int | |
| `page_no` | int nullable | |
| `content` | text | |
| `embedding` | vector(1536) | pgvector |
| `created_at` | timestamp | |

**`exam_generation_job`**

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | UUID PK | `jobId` |
| `user_id` | FK | |
| `document_id` | FK | |
| `options_json` | text | JobRequest.options |
| `status` | varchar | ExamJobStatus |
| `progress` | int | |
| `error_code` | varchar nullable | |
| `exam_id` | FK nullable | 완료 시 |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

**`generated_exam`**

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | UUID PK | `examId` |
| `job_id` | FK | |
| `document_id` | FK | |
| `user_id` | FK | |
| `title` | varchar | |
| `subject` | varchar | |
| `difficulty` | varchar | |
| `questions_json` | text | 전체 Question[] |
| `pdf_s3_key` | varchar nullable | |
| `created_at` | timestamp | |

---

## 8. Flutter 클라이언트 설계

### 8.1 파일 구조 (신규)

```text
lib/features/exam_generator/
├── exam_generator.dart           # 메인 화면
├── exam_document_model.dart      # 문서 DTO
├── exam_job_model.dart           # Job·Exam DTO
├── exam_question_model.dart      # Question DTO
├── exam_generator_service.dart   # Dio API
└── exam_generator_provider.dart  # Riverpod Notifier
```

- 라우터 [`router.dart`](../lib/core/router.dart): `/exam_generator` 추가.
- [`main_layout.dart`](../lib/layout/main_layout.dart) 사이드바: "시험 문제 생성" 메뉴 추가.

### 8.2 화면 구성 (`exam_generator.dart`)

| 영역 | 내용 |
|------|------|
| 헤더 | 제목, 과목 필터 |
| 좌측 | 문서 목록 (status 뱃지: 처리 중/준비됨/실패) |
| 우측 상단 | PDF 드래그·선택 업로드 (`file_picker`) |
| 우측 중단 | 선택 문서 `READY` 시 생성 옵션 폼 |
| 우측 하단 | Job 진행률·미리보기·PDF 다운로드 버튼 |

### 8.3 `ExamGeneratorService`

```dart
Future<ExamDocument> uploadDocument({
  required String filePath,
  String? title,
  String? subject,
});

Future<List<ExamDocument>> listDocuments({String? status});

Future<ExamDocument> getDocument(String documentId);

Future<ExamJob> createJob(String documentId, ExamJobOptions options);

Future<ExamJob> getJob(String jobId);

Future<GeneratedExam> getExam(String examId);

Future<void> downloadExamPdf(String examId, String savePath);
```

- 업로드: `FormData` + `MultipartFile.fromFile`.
- `examDioProvider`: `receiveTimeout: 120초`, 업로드 `sendTimeout: 120초`.

### 8.4 `ExamGeneratorNotifier` (Riverpod)

| 상태 | 설명 |
|------|------|
| `documents` | `AsyncValue<List<ExamDocument>>` |
| `selectedDocumentId` | 현재 선택 |
| `activeJob` | 생성 중 Job (폴링 대상) |
| `lastCompletedExam` | 다운로드·미리보기용 |
| `isUploading` | 업로드 중 |
| `lastError` | 스낵바용 |

**폴링:** `EXTRACTING`/`INDEXING`/`GENERATING`/`RENDERING`일 때 2~3초 간격 `Timer` → 완료·실패 시 중단.

### 8.5 의존성 추가 (`pubspec.yaml`)

| 패키지 | 용도 |
|--------|------|
| `file_picker` | PDF 파일 선택 |
| `path_provider` | 다운로드 저장 경로 |
| `open_file` (선택) | PDF 열기 |

---

## 9. exam_mode · ai_coach 연동 (Phase 6)

### 9.1 exam_mode

| 연동 | 설명 |
|------|------|
| D-Day 카드 | `generated_exam.subject` + 사용자 입력 시험일 (로컬 저장) |
| 오늘의 필수 목표 | "생성된 시험 N문제 풀기" 체크리스트 |
| 포모도로 | (선택) `examId`와 세션 연결 |

- MVP: `shared_preferences`에 `linkedExamId`, `examDate` 저장.

### 9.2 ai_coach

[`PLAN_AI_COACH.md`](PLAN_AI_COACH.md)의 `context` 객체 확장:

```json
{
  "today": "2026-05-28",
  "todos": [],
  "studyMaterials": [
    {
      "documentId": "uuid",
      "title": "3장_행렬식.pdf",
      "subject": "선형대수",
      "status": "READY"
    }
  ],
  "recentExams": [
    {
      "examId": "uuid",
      "title": "선형대수 — 3장 행렬식 연습",
      "questionCount": 10,
      "createdAt": "2026-05-28T11:01:30+09:00"
    }
  ]
}
```

- 빠른 제안 칩: "이 PDF 핵심 정리해줘", "약점 개념 복습 문제 추천해줘".
- RAG 검색은 **서버** `ai_coach` 또는 `exam/search` 재사용 — Flutter는 ID만 전달.

---

## 10. 구현 Phase

### Phase 0 — 설계 확정 (0.5~1일)

- [ ] Job 상태 enum·API 명세·에러 코드 확정 (본 문서 리뷰)
- [ ] Postgres + pgvector 확보
- [ ] **S3 버킷 생성** (`dontdelay-exam`, private, `ap-northeast-2`) + IAM user/role + Spring env
- [ ] OCR 방식 확정 (Tesseract 로컬 vs 클라우드)
- [ ] LaTeX 엔진 확정 (`pdflatex` vs `xelatex`, 한글 폰트)
- [ ] 임베딩·LLM 모델 확정 (AI 코치와 동일 벤더 권장)

### Phase 1 — 업로드 + S3 (3~4일)

- [ ] Spring: `StorageClient` + `S3StorageClient` (AWS SDK v2)
- [ ] Spring: `POST/GET /api/exam/documents`, S3 `PutObject`, `study_document` CRUD
- [ ] Security `/api/exam/**` authenticated
- [ ] Flutter: `exam_generator` 화면 껍데기 + 라우트·사이드바
- [ ] Flutter: PDF 업로드 + 문서 목록

**완료 기준:**

```bash
curl -c cookies.txt -X POST http://dontdelay.duckdns.org:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}'

curl -b cookies.txt -X POST http://dontdelay.duckdns.org:8080/api/exam/documents \
  -F "file=@sample.pdf" -F "subject=테스트"

curl -b cookies.txt http://dontdelay.duckdns.org:8080/api/exam/documents
```

→ `201` + S3·DB 확인.

### Phase 2 — 추출 + chunking + pgvector (4~6일)

- [ ] `DocumentIndexingJob` 비동기 파이프라인
- [ ] PDFBox 추출 + OCR fallback
- [ ] ChunkService + EmbeddingService + pgvector insert
- [ ] `GET /api/exam/documents/{id}` progress 갱신
- [ ] (선택) `POST .../reindex`, `DELETE ...`

**완료 기준:** 업로드 PDF → status `READY`, chunk_count > 0, DB embedding row 확인.

### Phase 3 — RAG 검색 (2~3일)

- [ ] `RagService` + `POST .../search`
- [ ] curl로 쿼리 품질 확인 (topK, minScore 튜닝)

**완료 기준:** subject 관련 쿼리에 적절한 chunk 반환.

### Phase 4 — 문제 생성 + LaTeX PDF (5~7일)

- [ ] `POST /api/exam/jobs`, `GET /api/exam/jobs/{id}`
- [ ] `ExamGenerationService` + `LlmClient` structured output
- [ ] `LatexPdfService` + S3 결과 PDF
- [ ] `GET /api/exam/exams/{id}`, `GET .../download`
- [ ] Rate limit·`DOCUMENT_NOT_READY` 검증

**완료 기준:** Job → `COMPLETED` → JSON + PDF 다운로드.

### Phase 5 — Flutter 상태·다운로드 UI (2~3일)

- [ ] 문서 status 폴링 UI
- [ ] 생성 옵션 폼 + Job 폴링
- [ ] 미리보기 + PDF 로컬 저장·열기
- [ ] 에러·Rate limit UX

**완료 기준:** 앱 E2E — PDF 업로드 → READY → 생성 → PDF 저장.

### Phase 6 — exam_mode · ai_coach 연동 (3~4일)

- [ ] exam_mode: 연결된 시험·목표 체크리스트
- [ ] `AiContextBuilder`에 `studyMaterials`·`recentExams` 추가
- [ ] ai_coach 빠른 제안 칩 확장

**완료 기준:** AI 코치가 업로드 PDF·생성 시험 맥락 반영 답변.

---

## 11. 일정 요약

| Phase | 기간(1인 기준) | 산출물 |
|-------|----------------|--------|
| 0 설계 | 0.5~1일 | API·인프라 확정 |
| 1 업로드 | 3~4일 | S3 + Flutter 업로드 |
| 2 인덱싱 | 4~6일 | pgvector READY |
| 3 RAG | 2~3일 | search API |
| 4 생성+PDF | 5~7일 | Job + LaTeX |
| 5 Flutter | 2~3일 | E2E UI |
| 6 연동 | 3~4일 | exam_mode·ai_coach |
| **합계** | **약 4~6주** | |

**권장 순서:** Phase 0 → 1 (curl) → 2 → 3 → 4 (curl) → 5 → 6.

**AI 코치와 병행 시:** Phase 0~1에서 `LlmClient`·Postgres·S3를 **한 번만** 구축. AI 코치 [`PLAN_AI_COACH.md`](PLAN_AI_COACH.md) Phase 1~2 완료 후 Phase 4(문제 생성) 착수 권장.

---

## 12. 테스트·확인 사항

### 백엔드

- [ ] 미로그인 `POST /api/exam/documents` → `401`
- [ ] 50MB 초과·비PDF → `400 INVALID_FILE`
- [ ] 타 user documentId → `404`
- [ ] `INDEXING` 중 생성 요청 → `409 DOCUMENT_NOT_READY`
- [ ] 빈 PDF → `FAILED` + `EMPTY_PDF`
- [ ] RAG search: 무관 쿼리 vs 관련 쿼리 score 차이
- [ ] LLM JSON 파싱 실패 → 재시도·`502`
- [ ] LaTeX 실패 시 JSON은 저장·PDF만 null

### Flutter

- [ ] 업로드 중 중복 클릭 방지
- [ ] 대용량 PDF 진행 표시
- [ ] 폴링 중 화면 이탈 후 복귀 시 상태 동기화
- [ ] 다운로드 경로·파일명
- [ ] 로그아웃 후 exam API → `401`

### 통합

- [ ] 업로드 → READY → 생성 → 다운로드 E2E
- [ ] 동일 PDF 재업로드 시 별도 documentId
- [ ] ai_coach context에 recentExams 반영

---

## 13. 제약 및 참고

| 항목 | 설명 |
|------|------|
| Spring 소스 | 본 저장소는 Flutter만 포함. 백엔드는 별도 Spring 프로젝트. |
| 인증 | [`auth_provider.dart`](../lib/features/auth/auth_provider.dart) |
| AI 코치 | [`PLAN_AI_COACH.md`](PLAN_AI_COACH.md) — LlmClient·인증 패턴 공유 |
| 시험기간 UI | [`exammode.dart`](../lib/features/exammode.dart) — 현재 더미 |
| 할 일 패턴 | [`todo_provider.dart`](../lib/features/todo/todo_provider.dart) — Service·Notifier 분리 참고 |
| 프로젝트 구조 | [`PROJECT_STRUCTURE.md`](PROJECT_STRUCTURE.md) |

---

## 14. 환경 변수 체크리스트 (운영)

```text
# Spring — 공통 AI
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini
OPENAI_EMBEDDING_MODEL=text-embedding-3-small

# Spring — Exam Generator
EXAM_GENERATOR_ENABLED=true
EXAM_MAX_UPLOAD_BYTES=52428800
EXAM_RATE_LIMIT_UPLOAD_PER_HOUR=10
EXAM_RATE_LIMIT_GENERATE_PER_DAY=20
EXAM_CHUNK_SIZE=800
EXAM_CHUNK_OVERLAP=120

# AWS S3 (확정 — exam PDF 원본·결과 저장)
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=dontdelay-exam
AWS_ACCESS_KEY_ID=...          # Spring 전용 IAM (Put/Get/Delete on users/*)
AWS_SECRET_ACCESS_KEY=...
AWS_S3_PRESIGNED_URL_TTL_MINUTES=15
# EC2/ECS 배포 시 ACCESS_KEY 대신 IAM Role 사용 권장

# PostgreSQL + pgvector
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...

# OCR (선택)
OCR_ENABLED=true
TESSERACT_LANG=kor+eng

# LaTeX
LATEX_BIN=/usr/bin/pdflatex
LATEX_FONT_PATH=/usr/share/fonts/noto-cjk
```

---

## 15. 변경 이력

| 일자 | 내용 |
|------|------|
| 2026-05-28 | 초안 작성 (PDF 업로드 · RAG · LaTeX · Flutter · exam_mode/ai_coach 연동) |
| 2026-05-28 | 파일 저장 **AWS S3 확정** — §2.1·§7.2 IAM·StorageClient·presigned URL 상세 추가 |
