#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$APP_DIR"

JAR="$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | sort -r | head -1)"
if [ -z "$JAR" ]; then
  echo "JAR not found under build/libs"
  exit 1
fi

mkdir -p run logs

if [ -f run/app.pid ]; then
  OLD_PID="$(cat run/app.pid)"
  if kill -0 "$OLD_PID" 2>/dev/null; then
    echo "Stopping PID $OLD_PID"
    kill "$OLD_PID"
    for _ in $(seq 1 30); do
      kill -0 "$OLD_PID" 2>/dev/null || break
      sleep 1
    done
    if kill -0 "$OLD_PID" 2>/dev/null; then
      kill -9 "$OLD_PID" 2>/dev/null || true
    fi
  fi
fi

export AWS_REGION="${AWS_REGION:-ap-northeast-2}"
export JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m}"

echo "Starting $JAR"
nohup java $JAVA_OPTS -jar "$JAR" --spring.profiles.active="${SPRING_PROFILES_ACTIVE:-prod}" \
  >> logs/app.log 2>&1 &
echo $! > run/app.pid
echo "Started PID $(cat run/app.pid)"
