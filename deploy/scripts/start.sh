#!/usr/bin/env bash
# 单机启动：仅通过 docker compose 拉起 postgres + app + nginx（无需巨型一体容器）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 优先使用安装目录；否则使用仓库 deploy/
if [[ -f "${SCRIPT_DIR}/../docker-compose.yml" ]]; then
  APP_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
elif [[ -f /opt/apps/kiftd/docker-compose.yml ]]; then
  APP_ROOT="/opt/apps/kiftd"
else
  APP_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
fi

cd "${APP_ROOT}"

if [[ ! -f dist/app.jar ]]; then
  echo "错误: 未找到 ${APP_ROOT}/dist/app.jar，请先执行 build.sh 与 install.sh" >&2
  exit 1
fi
if [[ ! -d dist/front ]] || [[ -z "$(ls -A dist/front 2>/dev/null || true)" ]]; then
  echo "错误: 未找到前端静态资源 ${APP_ROOT}/dist/front，请先执行 build.sh 与 install.sh" >&2
  exit 1
fi

if [[ ! -f .env ]] && [[ -f .env.example ]]; then
  cp -f .env.example .env
  echo "已生成 .env，请尽快修改 KIFTD_JWT_SECRET / 数据库口令 / 管理员口令"
fi

export KIFTD_HOST_ROOT="${KIFTD_HOST_ROOT:-${APP_ROOT}}"

echo "==> 在 ${APP_ROOT} 启动 kiftd (docker compose)"
docker compose --env-file .env up -d --build "$@"

echo ""
echo "服务已启动："
echo "  Web / 前端: http://localhost:${HTTP_PORT:-80}/"
echo "  API (经 nginx): http://localhost:${HTTP_PORT:-80}/api/"
echo "  WebDAV: http://localhost:${HTTP_PORT:-80}/webdav/"
echo "  查看日志: docker compose -f ${APP_ROOT}/docker-compose.yml logs -f"
