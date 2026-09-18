# kiftd Spring Boot 应用镜像（Eclipse Temurin 21 JRE，Ubuntu Jammy）
FROM eclipse-temurin:21-jre-jammy

LABEL org.opencontainers.image.title="kiftd-app" \
      org.opencontainers.image.description="kiftd Spring Boot backend"

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/*

# 容器内工作目录：/opt/app（宿主机 /opt/apps/kiftd 通过 compose 卷挂载 data/logs/config）
RUN mkdir -p /opt/app/data/filenodes \
             /opt/app/data/temp \
             /opt/app/config \
             /opt/app/logs

WORKDIR /opt/app

# 构建产物由 build.sh 放入 deploy/dist/（或 install 后的 /opt/apps/kiftd/dist）
COPY dist/app.jar /opt/app/app.jar
COPY application-prod.yml /opt/app/config/application-prod.yml

ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/opt/app/config/ \
    JAVA_OPTS="-Xms256m -Xmx1024m"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/system/ping" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/app/app.jar"]
