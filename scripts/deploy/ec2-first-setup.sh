#!/usr/bin/env bash
# EC2 최초 1회 실행 (Ubuntu 예시)
set -euo pipefail

APP_DIR="${1:-$HOME/DontDelay_SERVER}"
REPO_URL="${2:-}"

if [ -z "$REPO_URL" ]; then
  echo "Usage: bash ec2-first-setup.sh [APP_DIR] [GIT_REPO_URL]"
  echo "Example: bash ec2-first-setup.sh ~/DontDelay_SERVER git@github.com:USER/DontDelay_SERVER.git"
  exit 1
fi

sudo apt-get update
sudo apt-get install -y openjdk-17-jdk-headless git

if [ ! -d "$APP_DIR/.git" ]; then
  git clone "$REPO_URL" "$APP_DIR"
fi

cd "$APP_DIR"
chmod +x gradlew scripts/deploy/restart.sh
mkdir -p run logs

echo "Done. Next:"
echo "1) Attach IAM Role (S3) to this EC2 instance"
echo "2) Set GitHub Actions secrets: EC2_HOST, EC2_USER, EC2_SSH_KEY, EC2_APP_DIR=$APP_DIR"
echo "3) Push to main branch to trigger deploy"
