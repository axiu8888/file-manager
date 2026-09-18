#!/bin/bash

# crontab -e 
# 每5分钟执行一次 定期清理过期日志
# */5 * * * * /opt/app/cleanup_logs.sh

DIR="/opt/app"
cd $DIR || exit 1

name=$(ls -a | grep "\.jar$" | sort  -nr | head -n 1)
logFilename=$(echo "$name" | cut -f 1 -d '.')

LOG_DIR="$DIR/logs"
LOG_FILE="$logFilename.log"

RETENTION_DAYS=30
# 删除30天前的日志文件
find $LOG_DIR -name "*.tar.gz" -mtime +$RETENTION_DAYS -delete

# 如果当前日志文件超过指定大小，进行归档
MAX_SIZE=$((200 * 1024 * 1024)) # 200MB

if [ -f "$LOG_DIR/$LOG_FILE" ]; then
    # 修正stat命令用法
    file_size=$(stat -c%s "$LOG_DIR/$LOG_FILE" 2>/dev/null || stat -f%z "$LOG_DIR/$LOG_FILE" 2>/dev/null)
    echo "$LOG_FILE --> $((file_size / 1024 / 1024)) MB"
    
    if [ -n "$file_size" ] && [ "$file_size" -gt "$MAX_SIZE" ]; then
        cd $LOG_DIR || exit 1
        # 拷贝 并 清空日志文件
        DEST_NAME="${logFilename}__$(date +%Y%m%d%-H%M%S)"
        cp "$LOG_FILE" "$DEST_NAME.log" && echo ""> "$LOG_FILE"
        # 压缩日志文件
        tar -zcvf "$DEST_NAME.tar.gz" "$DEST_NAME.log" && rm -f "$DEST_NAME.log"
    fi
fi
