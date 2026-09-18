#!/bin/bash

# x64
wget https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_linux-x64_bin.tar.gz
tar -zxvf openjdk-21.0.2_linux-x64_bin.tar.gz
mv jdk-21.0.2 jdk_x86-64


# arm64
# wget https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_linux-aarch64_bin.tar.gz
# tar -zxvf openjdk-21.0.2_linux-aarch64_bin.tar.gz
# mv jdk-21.0.2 jdk_aarch64


# 建立软连接
bash ln_s_jdk.sh
