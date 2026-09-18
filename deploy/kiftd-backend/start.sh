#!/bin/bash
# 此命令在linux上时，建议通过 vim /yourdir/docker-entrypoint.sh编辑，拷贝，否则运行时可能会报错
export   LANG=C.UTF-8
#source /etc/profile
echo "Asia/shanghai" > /etc/timezone

#echo "work dir: $PWD"

cd /opt/app
# 倒序后，取第一个jar包
name=$(ls -a | grep "\.jar$" | sort  -nr | head -n 1)
logName=$(echo "$name" | cut -f 1 -d '.')

configname=$(ls | grep application. | sort -rn)
configfile=$(echo $configname|cut -d ' ' -f1)

mkdir -p logs && chmod 755 logs

exec java -jar \
  -Duser.timezone=GMT+08 \
  -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5005   \
  $name \
  --spring.config.location=$configfile \
  2>&1 | tee -a ./logs/$logName.log


