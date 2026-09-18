#!/bin/bash

# baseDir=/opt/programs/postgres
# baseDir=/opt/apps/postgres
baseDir=$PWD
# source $PWD/conf.properties

echo ""
echo "-----------------------------------"
name="postgres"
dir=$baseDir
echo "安装 $name, 目录：$dir"
docker run -d -it --privileged=true --restart=always \
  --network mynet --network-alias mynet-$name \
  --log-driver json-file \
  --log-opt max-file=1 \
  --log-opt max-size=100m \
  --shm-size=1g \
  -p 5432:5432/tcp \
  -v "/etc/localtime":/etc/localtime:ro \
  -v "$dir/data/":/var/lib/postgresql/data \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=123456 \
  -e TZ=Asia/Shanghai \
  -e LANG="C.utf8" \
  --name $name \
  postgres:17

