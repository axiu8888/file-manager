#!/bin/bash

# baseDir=/opt/programs/postgres
# baseDir=/opt/apps/postgres
baseDir=$PWD
# source $PWD/conf.properties


echo -e "\n-----------------------------------"
name=kiftd-backend
dir=$baseDir
echo "启动 $name, 目录：$dir"
docker run -d -it --privileged=true --restart=always \
  --network mynet --network-alias mynet-$name \
  -p 280:80/tcp \
  -v /etc/localtime:/etc/localtime:ro \
  -v "$dir/start.sh":/docker-entrypoint.sh \
  -v "$dir/data":/opt/app/data \
  -v "$dir":/opt/app/ \
  --name $name \
  hsrg-jdk:21

# nginx 在 mynet，后端在 znsxnet；接入 mynet 后才能被反代
docker network connect mynet "$name" 2>/dev/null || true

