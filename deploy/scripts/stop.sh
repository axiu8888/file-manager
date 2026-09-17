#!/usr/bin/env bash
# 停止 kiftd docker compose 服务
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -f "${SCRIPT_DIR}/../docker-compose.yml" ]]; then
  APP_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
elif [[ -f /opt/apps/kiftd/docker-compose.yml ]]; then
  APP_ROOT="/opt/apps/kiftd"
else
  APP_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
fi

cd "${APP_ROOT}"
echo "==> 在 ${APP_ROOT} 停止 kiftd"
if [[ -f .env ]]; then
  docker compose --env-file .env down "$@"
else
  docker compose down "$@"
fi
echo "已停止。"
