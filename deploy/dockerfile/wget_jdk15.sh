#!/bin/bash

# x64
wget https://download.java.net/java/GA/jdk15.0.2/0d1cfde4252546c6931946de8db48ee2/7/GPL/openjdk-15.0.2_linux-x64_bin.tar.gz
tar -zxvf openjdk-15.0.2_linux-x64_bin.tar.gz
mv jdk-15.0.2 jdk_x86-64

# arm64
# wget https://download.java.net/java/GA/jdk15.0.2/0d1cfde4252546c6931946de8db48ee2/7/GPL/openjdk-15.0.2_linux-aarch64_bin.tar.gz
# tar -zxvf openjdk-15.0.2_linux-aarch64_bin.tar.gz
# mv jdk-15.0.2 jdk_aarch64



# 建立软连接
bash ln_s_jdk.sh

