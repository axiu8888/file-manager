#!/bin/bash


echo -e "\n---------------------------\n"
echo "请主动替换此脚本文件"
echo "当前脚本目录: $PWD"
{
    echo "java版本 ==>: $(java --version)"
} || {
    echo "缺少java执行环境"
}
echo -e "\n---------------------------\n"

