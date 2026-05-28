# EC2 자동 배포 (GitHub Actions)

`main` 브랜치에 push되면 **앱 EC2**에 SSH 접속 후 **git pull → JAR 빌드 → 백그라운드 재시작**을 수행합니다.

PostgreSQL + pgvector는 **별도 DB EC2** (`dontdelay-DB`)에 둡니다. 생성·보안 그룹·설치는 [`DATABASE_EC2.md`](DATABASE_EC2.md)를 따르세요.

## 아키텍처

```text
GitHub Actions ──SSH──▶ [dontdelay-app EC2 :8080] ──5432──▶ [dontdelay-DB EC2]
                              │
                              └── S3 (IAM Role, exam 파일)
```

## 흐름

```text
git push main → GitHub Actions → SSH(앱 EC2) → git fetch/reset → ./gradlew bootJar → restart.sh
```

## 1. EC2 최초 설정 (1회)

```bash
# EC2 접속 후
bash -c "$(curl -fsSL https://raw.githubusercontent.com/YOUR_USER/DontDelay_SERVER/main/scripts/deploy/ec2-first-setup.sh)" \
  ~/DontDelay_SERVER git@github.com:YOUR_USER/DontDelay_SERVER.git
```

또는 저장소를 클론한 뒤:

```bash
chmod +x scripts/deploy/ec2-first-setup.sh scripts/deploy/restart.sh gradlew
bash scripts/deploy/ec2-first-setup.sh ~/DontDelay_SERVER git@github.com:YOUR_USER/DontDelay_SERVER.git
```

### Private 저장소 — Deploy Key

EC2에서 `git pull`이 되려면 배포 키가 필요합니다..

1. EC2에서: `ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N ""`
2. `cat ~/.ssh/github_deploy.pub` 내용을 GitHub repo → **Settings → Deploy keys** → Add (Read-only면 충분)
3. `~/.ssh/config`:

```sshconfig
Host github.com
  HostName github.com
  User git
  IdentityFile ~/.ssh/github_deploy
  IdentitiesOnly yes
```

4. 원격 URL: `git@github.com:USER/DontDelay_SERVER.git`

### IAM Role

**앱 EC2**에 S3 접근용 IAM Role을 연결합니다. Access Key는 불필요합니다. DB EC2에는 S3 Role이 필요 없습니다.

### PostgreSQL (DB EC2)

앱이 Postgres를 쓰도록 전환한 뒤, **앱 EC2**에만 다음 환경 변수를 설정합니다 (DB 프라이빗 IP는 [`DATABASE_EC2.md`](DATABASE_EC2.md) 참고).

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://<DB_PRIVATE_IP>:5432/dontdelay"
export SPRING_DATASOURCE_USERNAME=dontdelay_app
export SPRING_DATASOURCE_PASSWORD='...'
```

`restart.sh`는 `SPRING_PROFILES_ACTIVE=prod`를 사용하므로, 위 변수를 `~/.bashrc`, systemd `EnvironmentFile`, 또는 `run/env.sh` 등에 두고 재시작 전에 `source` 하면 됩니다.

## 2. GitHub Actions Secrets

Repository → **Settings → Secrets and variables → Actions → New repository secret**

| Secret | 예시 | 설명 |
|--------|------|------|
| `EC2_HOST` | `ec2-xx.ap-northeast-2.compute.amazonaws.com` 또는 Elastic IP | EC2 주소 |
| `EC2_USER` | `ubuntu` | SSH 사용자 |
| `EC2_SSH_KEY` | PEM private key 전체 | EC2 접속용 키 페어 **프라이빗** 키 |
| `EC2_APP_DIR` | `/home/ubuntu/DontDelay_SERVER` | EC2에 **실제로 clone한 절대 경로**. `~/...` 비권장(틀리기 쉬움) |
| `EC2_SSH_PORT` | `22` | (선택) 기본 22 |

`EC2_SSH_KEY`는 `.pem` 파일 내용을 그대로 붙여넣습니다 (`-----BEGIN ...` ~ `-----END ...`).

## 3. EC2 보안 그룹

GitHub Actions는 고정 IP가 없습니다. 배포용으로 다음 중 하나를 선택합니다.

- **권장:** 배포 전용 EC2에 **본인 IP만** SSH 허용 + Actions용 **self-hosted runner** (고급)
- **간단(MVP):** 보안 그룹 SSH(22)를 `0.0.0.0/0`으로 열고, 키 기반 인증만 사용 (비밀번호 로그인 OFF)

## 4. 수동 재시작 (EC2)

```bash
cd ~/DontDelay_SERVER
git pull origin main
./gradlew bootJar -x test --no-daemon
bash scripts/deploy/restart.sh
tail -f logs/app.log
```

## 5. 확인

```bash
curl http://localhost:8080/api/health
cat run/app.pid
```

## 6. 트러블슈팅

| 증상 | 확인 |
|------|------|
| Actions SSH 실패 | `EC2_HOST`, 보안 그룹 22, `EC2_SSH_KEY` 형식 |
| `cd: No such file or directory` | EC2에 해당 경로 없음 → clone 먼저. Secret은 `/home/ubuntu/DontDelay_SERVER` 형식 |
| `git fetch` 실패 | Deploy key, `git remote -v` |
| `gradlew: Permission denied` | `chmod +x gradlew` |
| S3 Access Denied | EC2 IAM Role, 버킷 이름 |
| DB 연결 실패 | [`DATABASE_EC2.md`](DATABASE_EC2.md) — SG 5432, `pg_hba`, 프라이빗 IP |
| 포트 충돌 | `run/app.pid` 프로세스, `ss -lntp \| grep 8080` |
