#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/travel-agent}"
JAR_PATH="${JAR_PATH:-$APP_HOME/app.jar}"
ENV_FILE="${ENV_FILE:-$APP_HOME/.env}"
LOG_FILE="${LOG_FILE:-$APP_HOME/log/app.log}"
PID_FILE="${PID_FILE:-$APP_HOME/app.pid}"
JAVA_CMD="${JAVA_CMD:-java}"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "jar not found: $JAR_PATH"
  exit 1
fi

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "already running: $(cat "$PID_FILE")"
  exit 0
fi

if [[ -f "$ENV_FILE" ]]; then
  set -a
  source "$ENV_FILE"
  set +a
fi

: "${SPRING_PROFILES_ACTIVE:=prod}"
export SPRING_PROFILES_ACTIVE

mkdir -p "$(dirname "$LOG_FILE")"
touch "$LOG_FILE"

nohup "$JAVA_CMD" -jar "$JAR_PATH" >> "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

echo "started: $(cat "$PID_FILE")"
echo "log: $LOG_FILE"
