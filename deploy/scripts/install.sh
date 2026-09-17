#!/usr/bin/env bash
# 在 Ubuntu 宿主机创建 /opt/apps/kiftd 目录布局，并复制 compose / 配置 / 构建产物
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY_DIR="${ROOT_DIR}/deploy"
TARGET="${KIFTD_HOST_ROOT:-/opt/apps/kiftd}"

echo "==> 安装目标: ${TARGET}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "提示: 写入 ${TARGET} 通常需要 root，将尝试使用 sudo。"
  SUDO="sudo"
else
  SUDO=""
fi

$SUDO mkdir -p \
  "${TARGET}/data/filenodes" \
  "${TARGET}/data/temp" \
  "${TARGET}/data/postgres" \
  "${TARGET}/logs" \
  "${TARGET}/config" \
  "${TARGET}/dist/front" \
  "${TARGET}/scripts"

copy_file() {
  local src="$1" dest="$2"
  if [[ -f "${src}" ]]; then
    $SUDO cp -f "${src}" "${dest}"
  else
    echo "警告: 缺少文件 ${src}" >&2
  fi
}

copy_file "${DEPLOY_DIR}/docker-compose.yml" "${TARGET}/docker-compose.yml"
copy_file "${DEPLOY_DIR}/Dockerfile.app" "${TARGET}/Dockerfile.app"
copy_file "${DEPLOY_DIR}/Dockerfile.nginx" "${TARGET}/Dockerfile.nginx"
copy_file "${DEPLOY_DIR}/nginx.conf" "${TARGET}/nginx.conf"
copy_file "${DEPLOY_DIR}/application-prod.yml" "${TARGET}/config/application-prod.yml"
copy_file "${DEPLOY_DIR}/application-prod.yml" "${TARGET}/application-prod.yml"

if [[ -f "${DEPLOY_DIR}/.env" ]]; then
  copy_file "${DEPLOY_DIR}/.env" "${TARGET}/.env"
elif [[ -f "${DEPLOY_DIR}/.env.example" ]]; then
  if [[ ! -f "${TARGET}/.env" ]]; then
    copy_file "${DEPLOY_DIR}/.env.example" "${TARGET}/.env"
    echo "==> 已从 .env.example 生成 ${TARGET}/.env（请修改密钥与口令）"
  fi
fi

# 构建产物
if [[ -f "${DEPLOY_DIR}/dist/app.jar" ]]; then
  $SUDO cp -f "${DEPLOY_DIR}/dist/app.jar" "${TARGET}/dist/app.jar"
  echo "==> 已复制 app.jar"
else
  echo "警告: ${DEPLOY_DIR}/dist/app.jar 不存在，请先运行 build.sh" >&2
fi

if [[ -d "${DEPLOY_DIR}/dist/front" ]] && [[ -n "$(ls -A "${DEPLOY_DIR}/dist/front" 2>/dev/null || true)" ]]; then
  $SUDO rm -rf "${TARGET}/dist/front"
  $SUDO mkdir -p "${TARGET}/dist/front"
  $SUDO cp -a "${DEPLOY_DIR}/dist/front/." "${TARGET}/dist/front/"
  echo "==> 已复制前端静态资源"
else
  echo "警告: ${DEPLOY_DIR}/dist/front 为空，请先运行 build.sh" >&2
fi

# 脚本
$SUDO cp -f "${DEPLOY_DIR}/scripts/"*.sh "${TARGET}/scripts/"
$SUDO chmod +x "${TARGET}/scripts/"*.sh

# README
if [[ -f "${DEPLOY_DIR}/README.md" ]]; then
  $SUDO cp -f "${DEPLOY_DIR}/README.md" "${TARGET}/README.md"
fi

# 数据目录权限：应用容器用户可能为非 root，放宽写权限便于卷挂载
$SUDO chmod -R a+rwX "${TARGET}/data" "${TARGET}/logs" || true

echo ""
echo "安装完成。目录布局："
echo "  ${TARGET}/docker-compose.yml"
echo "  ${TARGET}/Dockerfile.app / Dockerfile.nginx / nginx.conf"
echo "  ${TARGET}/config/application-prod.yml"
echo "  ${TARGET}/dist/app.jar"
echo "  ${TARGET}/dist/front/"
echo "  ${TARGET}/data/{filenodes,temp,postgres}"
echo "  ${TARGET}/logs/"
echo ""
echo "启动: ${TARGET}/scripts/start.sh"
echo "  或: cd ${TARGET} && docker compose up -d --build"
