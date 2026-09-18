#!/bin/bash


dir=$PWD

arch=$(uname -m)
echo "当前系统架构: $arch"

if [[ "$arch" == "aarch64" || "$arch" == "arm64" || "$arch" == arm* ]]; then
    # exec /start_arm64.sh
    echo "ARM架构 ==>: cp -r $dir/jdk && cp -r $dir/jdk_aarch64 $dir/jdk"
    rm -f $dir/jdk && cp -r $dir/jdk_aarch64 $dir/jdk
else
    # exec /start_amd64.sh
    echo "非ARM架构  ==>: rm -f $dir/jdk && cp -r $dir/jdk_x86-64 $dir/jdk"
    rm -f $dir/jdk && cp -r $dir/jdk_x86-64 $dir/jdk
fi



