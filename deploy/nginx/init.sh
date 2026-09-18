#!/bin/bash

# baseDir=/opt/programs
# baseDir=/opt/apps
baseDir=$PWD
# source $PWD/conf.properties

echo ""
echo "-----------------------------------"
name="nginx"
dir=$baseDir
echo "安装 $name, 目录：$dir"
# mkdir -p $dir && cp -r $PWD/programs/nginx/* $dir
docker run --privileged -d --restart=always \
  --network mynet --network-alias mynet-$name \
  --log-driver json-file \
  --log-opt max-file=1 \
  --log-opt max-size=100m \
  -p 980:80 \
  -p 9443:443 \
  -v "$dir/share/":/usr/share/nginx/ \
  -v "$dir/conf.d/":/etc/nginx/conf.d/ \
  -v "$dir/var":/var/lib/nginx \
  -v "$dir/log":/var/log/nginx \
  -e TZ=Asia/Shanghai \
  -e LANG="C.utf8" \
  --name $name \
  nginx

# echo "拷贝nginx.conf文件"
# cd $dir || exit
#docker cp ./nginx.conf nginx:/etc/nginx/nginx.conf
