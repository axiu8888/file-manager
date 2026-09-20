#!/bin/bash
# 可选：宿主机 crontab 调用 docker exec postgres pg_dump（应用内已有定时备份，一般不用本脚本）
# 在 Docker 宿主机（或 WSL）上跑，不要放进 kiftd-backend 容器。
#
# crontab -e
# 每天 23:55：
#   55 23 * * * /path/to/backup-db.sh >> /path/to/backup-db.log 2>&1

set -euo pipefail

PG_CONTAINER="${PG_CONTAINER:-postgres}"
PGUSER="${PGUSER:-postgres}"
PGDATABASE="${PGDATABASE:-postgres}"
BACKUP_DIR="${BACKUP_DIR:-/mnt/e/develop/docker/psql-backup}"
KEEP_DAYS="${KEEP_DAYS:-30}"
STAMP="$(TZ=Asia/Shanghai date +%Y%m%d)"

mkdir -p "$BACKUP_DIR"
STATE="$BACKUP_DIR/.last-fingerprint"
OUT="$BACKUP_DIR/kiftd-${STAMP}.dump"

log() { echo "[$(TZ=Asia/Shanghai date '+%F %T')] $*"; }

if ! docker inspect "$PG_CONTAINER" >/dev/null 2>&1; then
  log "错误: 找不到容器 $PG_CONTAINER"
  exit 1
fi

psql() {
  docker exec -i "$PG_CONTAINER" psql -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 "$@"
}

# 文件 + 文件夹的内容指纹（含新增/改名/移动/删除）
fingerprint="$(psql -Atc "
SELECT md5(COALESCE(string_agg(h, '' ORDER BY h), ''))
FROM (
  SELECT md5(file_id || chr(31) || file_name || chr(31) || file_parent_folder || chr(31) || file_path) AS h
    FROM file_node
  UNION ALL
  SELECT md5(folder_id || chr(31) || folder_name || chr(31) || COALESCE(folder_parent, ''))
    FROM folder
) q;
")"

if [[ -z "$fingerprint" ]]; then
  log "错误: 无法计算指纹，检查库表 file_node / folder"
  exit 1
fi

if [[ -f "$STATE" ]] && [[ "$(cat "$STATE")" == "$fingerprint" ]]; then
  log "今天相对上次备份无文件变动，跳过导出"
  exit 0
fi

# 同一天已有备份则覆盖（多次触发只留一份）
log "检测到文件变动，开始导出 $PGDATABASE -> $OUT"
docker exec "$PG_CONTAINER" pg_dump -U "$PGUSER" -d "$PGDATABASE" -Fc --no-owner --no-acl > "$OUT.tmp"
mv -f "$OUT.tmp" "$OUT"
echo "$fingerprint" > "$STATE"
log "导出完成 $(du -h "$OUT" | awk '{print $1}')"

if [[ "$KEEP_DAYS" =~ ^[0-9]+$ ]] && [[ "$KEEP_DAYS" -gt 0 ]]; then
  find "$BACKUP_DIR" -maxdepth 1 -type f -name 'kiftd-*.dump' -mtime "+$KEEP_DAYS" -delete
  log "已清理 ${KEEP_DAYS} 天前的备份"
fi
