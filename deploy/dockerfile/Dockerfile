# 基础镜像
# FROM arm64v8/ubuntu
FROM ubuntu
#作者
MAINTAINER dingxiuan <dingxiuan@163.com>
# 版本
LABEL version=1.0  description=hsrg-jdk:v2

# 替换为阿里云的源
RUN sed -i 's|http://.*debian.org|https://mirrors.aliyun.com|g' /etc/apt/sources.list
RUN sed -i 's|http://.*archive.ubuntu.com|https://mirrors.aliyun.com|g' /etc/apt/sources.list
RUN sed -i 's|http://.*security.ubuntu.com|https://mirrors.aliyun.com|g' /etc/apt/sources.list

# 适配部分docker不支持的问题
RUN apt-get update && apt-get upgrade -y && apt-get update -y \
 && apt-get install -y libltdl7 fontconfig libfreetype6 \
 && apt-get install -y cron vim curl wget zip tree net-tools tcpdump iputils-ping \
 && apt-get install -y docker-cli \
 && apt-get clean

# ENV TIME_ZONE Asia/Shanghai
#系统编码
#ENV LANG=C.UTF-8
ENV JAVA_HOME=/opt/env/jdk
ENV JRE_HOME=$JAVA_HOME/jre
ENV CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar
ENV PATH=$JAVA_HOME/bin:$PATH
#声明一个挂载点，容器内此路径会对应宿主机的某个文件夹
VOLUME /opt/env/
VOLUME /opt/app/
RUN mkdir -p /opt/env/ && chmod 755 /opt/env/ && mkdir -p /opt/app/ && chmod 755 /opt/app/
ADD jdk /opt/env/jdk

ADD docker-entrypoint.sh /
RUN chmod 755 /docker-entrypoint.sh

# 工作目录
WORKDIR /opt/app/
#启动容器时的进程
ENTRYPOINT ["/docker-entrypoint.sh"]
#HEALTHCHECK --interval=30s --timeout=30s CMD curl -f http://localhost:80/actuator/health || exit 1
#暴露80端口
#EXPOSE 80/tcp
#EXPOSE 80/udp
#--build-arg=[] :设置镜像创建时的变量；
#-f :指定要使用的Dockerfile路径；
#--force-rm :设置镜像过程中删除中间容器；
#--rm :设置镜像成功后删除中间容器；
#--tag, -t: 镜像的名字及标签，通常 name:tag 或者 name 格式；
# 拷贝Dockerfile和start.sh文件到centos，然后执行如下命令
# docker build -f Dockerfile --rm  --tag arm64v8/hsrg-jdk:21 .
# docker build -f Dockerfile --rm  --tag hsrg-jdk:21 .

