#!/usr/bin/env bash
# 构建前端 dist + 后端 jar，复制到 deploy/dist（供 Docker 镜像构建）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY_DIR="${ROOT_DIR}/deploy"
DIST_DIR="${DEPLOY_DIR}/dist"
FRONT_DIR="${ROOT_DIR}/kiftd-front"
BACK_DIR="${ROOT_DIR}/kiftd-backend"

echo "==> 项目根目录: ${ROOT_DIR}"
mkdir -p "${DIST_DIR}/front"

# ---------- 前端 ----------
echo "==> 构建前端 (npm)"
cd "${FRONT_DIR}"
if [[ ! -d node_modules ]]; then
  npm ci --prefer-offline || npm install
fi
npm run build
rm -rf "${DIST_DIR}/front"
mkdir -p "${DIST_DIR}/front"
cp -a "${FRONT_DIR}/dist/." "${DIST_DIR}/front/"
echo "==> 前端已复制到 ${DIST_DIR}/front"

# ---------- 后端 ----------
echo "==> 构建后端 jar (Gradle)"
cd "${BACK_DIR}"

run_gradle() {
  if [[ -x ./gradlew ]]; then
    # 仓库内 gradlew 可能指向本机 GRADLE_HOME；容器/CI 可设 GRADLE_HOME 或使用系统 gradle
    if [[ -n "${GRADLE_HOME:-}" && -x "${GRADLE_HOME}/bin/gradle" ]]; then
      ./gradlew "$@"
    elif command -v gradle >/dev/null 2>&1; then
      gradle "$@"
    elif [[ -x ./gradlew ]]; then
      ./gradlew "$@"
    else
      echo "错误: 未找到 gradle。请安装 Gradle 8.14+ 或设置 GRADLE_HOME。" >&2
      exit 1
    fi
  elif command -v gradle >/dev/null 2>&1; then
    gradle "$@"
  else
    echo "错误: 未找到 gradle / gradlew。" >&2
    exit 1
  fi
}

run_gradle --no-daemon clean bootJar -x test

JAR_SRC="$(find "${BACK_DIR}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)"
if [[ -z "${JAR_SRC}" ]]; then
  echo "错误: 未找到 bootJar 产物 (build/libs/*.jar)" >&2
  exit 1
fi
cp -f "${JAR_SRC}" "${DIST_DIR}/app.jar"
echo "==> 后端 jar 已复制到 ${DIST_DIR}/app.jar ($(basename "${JAR_SRC}"))"

# 同步生产配置到 dist 旁，便于 install 一并拷贝
cp -f "${DEPLOY_DIR}/application-prod.yml" "${DIST_DIR}/application-prod.yml" 2>/dev/null || true

echo ""
echo "构建完成。"
echo "  ${DIST_DIR}/app.jar"
echo "  ${DIST_DIR}/front/"
echo "下一步: ./deploy/scripts/install.sh  或直接在 deploy/ 下 docker compose build"
