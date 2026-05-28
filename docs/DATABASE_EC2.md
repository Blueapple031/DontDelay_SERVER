# dontdelay-DB — PostgreSQL + pgvector (전용 EC2)

Spring 앱 EC2와 **분리된** DB 전용 인스턴스입니다. 메타데이터·벡터 임베딩(pgvector)는 여기에 두고, PDF·시험 파일은 S3에 둡니다.

> **리전:** 앱 EC2와 DB EC2는 **같은 리전·같은 VPC**여야 프라이빗 IP(`172.31.x.x`)로 5432 접속이 됩니다. 리전이 다르면 타임아웃됩니다.

```text
[dontdelay-app EC2]  ──5432──▶  [dontdelay-DB EC2]
       │                              PostgreSQL 16 + pgvector
       └── S3 (IAM Role)
```

---

## 1. EC2 인스턴스 생성 (AWS 콘솔)

스크린샷에서 선택한 AMI는 **SQL Server 2022가 포함된 이미지**입니다. PostgreSQL용으로는 **아래처럼 바꿔야** 합니다.

| 항목 | 권장 값 | 비고 |
|------|---------|------|
| **이름** | `dontdelay-DB` | 그대로 사용 가능 |
| **AMI** | **Ubuntu Server 22.04 LTS (HVM), SSD Volume Type** | `ami-0c2c…` 등 **일반 Ubuntu 22.04**. **SQL Server 포함 AMI 선택 금지** |
| **인스턴스 유형** | `t3.medium` (2 vCPU, 4 GiB) | MVP·소규모 pgvector에 적당. 트래픽 증가 시 `t3.large` |
| **키 페어** | 앱 EC2와 동일 또는 DB 전용 | SSH 관리용 |
| **스토리지** | gp3 **30 GiB** 이상 | DB + WAL 여유 |
| **VPC** | 앱 EC2와 **동일 VPC** | Private IP로 5432 접속 |
| **퍼블릭 IP** | 없어도 됨 (SSH는 Session Manager 또는 Bastion) | DB는 **프라이빗 서브넷** 권장 |

### 보안 그룹 (`dontdelay-db-sg` 예시)

| 유형 | 포트 | 소스 | 설명 |
|------|------|------|------|
| Custom TCP | **5432** | **앱 EC2 보안 그룹 ID** (`sg-…`) | Spring → Postgres (가장 안전) |
| Custom TCP | 5432 | 앱 EC2 **프라이빗 IP/32** | SG 대신 IP 고정 시 |
| SSH | 22 | **본인 IP/32** | 관리용만 (0.0.0.0/0 비권장) |

**5432를 `0.0.0.0/0`으로 열지 마세요.**

앱 EC2 보안 그룹에는 **아웃바운드 5432 → DB SG**가 기본 허용되는 경우가 많습니다. 막혀 있으면 아웃바운드도 확인하세요.

---

## 2. DB EC2 최초 설정

### 2.1 SSH 접속

```bash
ssh -i your-key.pem ubuntu@<DB_PUBLIC_IP_OR_BASTION>
```

### 2.2 설치 스크립트 실행

저장소를 클론했거나 스크립트만 복사한 뒤:

```bash
# 비밀번호를 환경 변수로 넘기면 프롬프트 생략 가능
export DB_PASSWORD='강한_랜덤_비밀번호'
sudo -E bash scripts/deploy/ec2-postgres-pgvector-setup.sh dontdelay dontdelay_app
```

수동 설치가 필요하면 동일 내용: PostgreSQL **16** (PGDG) + 패키지 `postgresql-16-pgvector`, DB `dontdelay`, 확장 `CREATE EXTENSION vector`.

### 2.3 `pg_hba.conf` CIDR 수정

스크립트는 AWS 기본 VPC용 **`172.31.0.0/16`** 을 넣습니다. 더 좁히려면 **앱 EC2 프라이빗 IP/32** 로 바꿉니다 (예: 서울 앱 `172.31.44.107/32`).

```bash
sudo nano /etc/postgresql/16/main/pg_hba.conf
# 예: host  dontdelay  dontdelay_app  172.31.44.107/32  scram-sha-256
sudo systemctl restart postgresql
```

설치 시 앱 IP만 허용: `sudo APP_CIDR=172.31.44.107/32 bash scripts/deploy/ec2-postgres-pgvector-setup.sh`

### 2.4 동작 확인 (DB EC2에서)

```bash
sudo -u postgres psql -d dontdelay -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"
sudo -u postgres psql -d dontdelay -c "\dx"
```

---

## 3. 앱 EC2(Spring) 연결 설정

DB EC2의 **프라이빗 IP**를 사용합니다 (같은 VPC·같은 리전).

```bash
cd ~/DontDelay_SERVER
cp scripts/deploy/env.example run/env.sh
nano run/env.sh   # DB 프라이빗 IP·비밀번호 입력
chmod 600 run/env.sh
bash scripts/deploy/restart.sh   # restart.sh가 run/env.sh를 source함
```

`run/env.sh` 없으면 prod는 기존처럼 **H2 파일 DB**로 기동합니다.

연결 테스트 (앱 EC2에서):

```bash
sudo apt-get install -y postgresql-client
psql "postgresql://dontdelay_app:PASSWORD@10.0.1.xx:5432/dontdelay" -c "SELECT 1;"
```

> **참고:** 현재 `application-prod.yml`은 H2 파일 DB입니다. Postgres로 전환할 때는 `build.gradle`에 `runtimeOnly 'org.postgresql:postgresql'` 추가 및 JPA `ddl-auto` 정책을 검토하세요. 연결 정보만 위 환경 변수로 덮어쓸 수 있습니다.

---

## 4. Spring / Exam Generator 연동 체크리스트

- [ ] DB EC2: PostgreSQL 16 + `vector` 확장 설치
- [ ] 보안 그룹: 5432 ← 앱 EC2만
- [ ] `pg_hba.conf` CIDR 실제 네트워크에 맞게 수정
- [ ] 앱 EC2: `SPRING_DATASOURCE_*` 환경 변수 설정
- [ ] (코드 전환 후) 마이그레이션·`exam_chunk.embedding vector(1536)` 등 — [`PLAN_EXAM_GENERATOR.md`](PLAN_EXAM_GENERATOR.md) §7.5

환경 변수 전체 목록은 [`PLAN_EXAM_GENERATOR.md`](PLAN_EXAM_GENERATOR.md) §14를 참고하세요.

---

## 5. 운영·백업 (MVP)

| 작업 | 명령 |
|------|------|
| 상태 | `sudo systemctl status postgresql` |
| 로그 | `sudo tail -f /var/log/postgresql/postgresql-16-main.log` |
| 논리 백업 | `pg_dump -h localhost -U dontdelay_app dontdelay > backup.sql` |
| EBS 스냅샷 | AWS 콘솔 → EC2 → 볼륨 → Create snapshot (주기 권장) |

---

## 6. 트러블슈팅

| 증상 | 확인 |
|------|------|
| `Connection refused` | DB SG 5432, Postgres `listen_addresses = '*'`, 서비스 실행 여부 |
| `password authentication failed` | `DB_PASSWORD`, `pg_hba` scram-sha-256, 사용자명 |
| `no pg_hba.conf entry` | `pg_hba`에 앱 IP/CIDR 추가 후 `systemctl restart postgresql` |
| `extension "vector" not found` | `postgresql-16-pgvector` 설치, `CREATE EXTENSION vector` |
| SQL Server AMI로 생성함 | 인스턴스 **종료 후 일반 Ubuntu 22.04 AMI로 재생성** (SQL Server AMI는 라이선스·리소스 낭비) |

---

## 7. 관련 문서

- 앱 배포: [`DEPLOY.md`](DEPLOY.md)
- Exam Generator·스키마: [`PLAN_EXAM_GENERATOR.md`](PLAN_EXAM_GENERATOR.md)
