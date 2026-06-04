# Exam Generator — 진행 현황

> 기준: 2026-05-29 · 상세 계획: [`PLAN_EXAM_GENERATOR.md`](PLAN_EXAM_GENERATOR.md)

## DB (현재)

| 항목 | 값 |
|------|-----|
| **엔진** | H2 파일 DB |
| **로컬** | `jdbc:h2:file:./data/dontdelay-db` (`application.yml`) |
| **운영(prod)** | 동일 경로 기본값 (`application-prod.yml`) |
| **임베딩** | `document_chunk.embedding_data` (BLOB, float[]) |
| **Postgres** | 보류 — 전환 시 [`DATABASE_EC2.md`](DATABASE_EC2.md) |

EC2에 Postgres용 `run/env.sh`가 있으면 **DB 관련 export를 제거**하거나 주석 처리하세요. 없으면 prod 기본값이 H2입니다.

## Phase 진행

| Phase | 상태 |
|-------|------|
| 0~1 업로드·S3·문서 API | ✅ |
| 2 인덱싱 (PDFBox·Upstage OCR·chunk·임베딩) | ✅ |
| 3 RAG search | ⏳ |
| 4~6 생성·Flutter·연동 | ⏳ |

## 검증 curl

```bash
curl -c cookies.txt -X POST http://dontdelay.duckdns.org:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"test","password":"test"}'

curl -b cookies.txt -X POST http://dontdelay.duckdns.org:8080/api/exam/documents \
  -F "file=@sample.pdf" -F "subject=테스트"

curl -b cookies.txt http://dontdelay.duckdns.org:8080/api/exam/documents/{documentId}
```

**Phase 2 완료:** `status: READY`, `chunkCount > 0`

## 관련 문서

- [`DEPLOY.md`](DEPLOY.md) — EC2 배포 (H2 로컬)
- [`DATABASE_EC2.md`](DATABASE_EC2.md) — Postgres 전환 (예정)
- [`API_SPECIFICATION.md`](API_SPECIFICATION.md)
